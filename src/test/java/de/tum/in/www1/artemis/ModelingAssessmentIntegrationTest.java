package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.*;

import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ModelingAssessmentIntegrationTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ModelingSubmissionService modelSubmissionService;

    @Autowired
    ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ParticipationService participationService;

    private ModelingExercise classExercise;

    private ModelingExercise activityExercise;

    private ModelingExercise objectExercise;

    private ModelingExercise useCaseExercise;

    @Before
    public void initTestCase() throws Exception {
        database.resetDatabase();
        database.addUsers(2, 1);
        database.addCourseWithDifferentModelingExercises();
        classExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        activityExercise = (ModelingExercise) exerciseRepo.findAll().get(1);
        objectExercise = (ModelingExercise) exerciseRepo.findAll().get(2);
        useCaseExercise = (ModelingExercise) exerciseRepo.findAll().get(3);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void manualAssessmentSubmitAsStudent() throws Exception {
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put("/api/modeling-submissions/" + submission.getId() + "/feedback?submit=true", feedbacks, HttpStatus.FORBIDDEN);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        assertThat(storedResult.getFeedbacks()).as("feedback has not been set").isNullOrEmpty();
        assertThat(storedResult.isRated() == null || !storedResult.isRated()).as("rated has not been set").isTrue();
        assertThat(storedResult.getScore()).as("score hasnt been calculated").isNull();
        assertThat(storedResult.getAssessor()).as("Assessor has been set").isNull();
        assertThat(storedResult.getResultString()).as("result string has not been set").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSave() throws Exception {
        User assessor = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put("/api/modeling-submissions/" + submission.getId() + "/feedback", feedbacks, HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks());
        checkResultAfterSave(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSubmit_classDiagram() throws Exception {
        User assessor = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put("/api/modeling-submissions/" + submission.getId() + "/feedback?submit=true", feedbacks, HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks());
        checkResultAfterSubmit(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSubmit_activityDiagram() throws Exception {
        User assessor = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission = database.addModelingSubmissionFromResources(activityExercise, "test-data/model-submission/activity-model.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/activity-assessment.json");
        request.put("/api/modeling-submissions/" + submission.getId() + "/feedback?submit=true", feedbacks, HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks());
        checkResultAfterSubmit(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSubmit_objectDiagram() throws Exception {
        User assessor = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission = database.addModelingSubmissionFromResources(objectExercise, "test-data/model-submission/object-model.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/object-assessment.json");
        request.put("/api/modeling-submissions/" + submission.getId() + "/feedback?submit=true", feedbacks, HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks());
        checkResultAfterSubmit(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSubmit_useCaseDiagram() throws Exception {
        User assessor = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/use-case-assessment.json");
        request.put("/api/modeling-submissions/" + submission.getId() + "/feedback?submit=true", feedbacks, HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks());
        checkResultAfterSubmit(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSaveAndSubmit() throws Exception {
        User assessor = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put("/api/modeling-submissions/" + submission.getId() + "/feedback", feedbacks, HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks());
        checkResultAfterSave(storedResult, assessor);
        feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.v2.json");
        request.put("/api/modeling-submissions/" + submission.getId() + "/feedback?submit=true", feedbacks, HttpStatus.OK);
        storedSubmission = modelingSubmissionRepo.findById(submission.getId()).get();
        storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks());
        checkResultAfterSubmit(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void automaticAssessmentUponModelSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "tutor1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put("/api/modeling-submissions/" + submission1.getId() + "/feedback?submit=true", feedbacks, HttpStatus.OK);
        ModelingSubmission storedSubmission1 = modelingSubmissionRepo.findById(submission1.getId()).get();
        Result result = storedSubmission1.getResult();

        // TODO CZ: for some reason the following line does not work due to org.hibernate.HibernateException: null index column for collection Result.feedbacks
        // Result result = database.addModelingAssessmentForSubmission(classExercise, submission1, "test-data/model-assessment/assessment.54727.json", "tutor1");

        ModelingSubmission submission2 = ModelFactory.generateModelingSubmission(database.loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        submission2 = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission2, ModelingSubmission.class, HttpStatus.OK);
        await().atMost(10, TimeUnit.SECONDS).alias("2nd submission has been automatically assessed").until(submissionHasBeenAssessed(submission2.getId()));
        ModelingSubmission storedSubmission2 = modelingSubmissionRepo.findById(submission2.getId()).get();
        Result storedResult2 = storedSubmission2.getResult();
        assertThat(result.getScore()).as("identical model got assessed equally").isEqualTo(storedResult2.getScore());
        assertThat(storedResult2.getAssessmentType()).as("got assessed automatically").isEqualTo(AssessmentType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void automaticAssessmentUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put("/api/modeling-submissions/" + submission1.getId() + "/feedback?submit=true", feedbacks, HttpStatus.OK);
        ModelingSubmission storedSubmission1 = modelingSubmissionRepo.findById(submission1.getId()).get();
        Result storedResult1 = storedSubmission1.getResult();
        await().atMost(10, TimeUnit.SECONDS).alias("2nd submission has been automatically assessed").until(submissionHasBeenAssessed(submission2.getId()));
        ModelingSubmission storedSubmission2 = modelingSubmissionRepo.findById(submission2.getId()).get();
        Result storedResult2 = storedSubmission2.getResult();
        assertThat(storedResult1.getScore()).as("identical model got assessed equally").isEqualTo(storedResult2.getScore());
        assertThat(storedResult2.getAssessmentType()).as("got assessed automatically").isEqualTo(AssessmentType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testConflictDetection() throws Exception {
        User assessor = userRepo.findOneByLogin("tutor1").get();
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.conflict.1.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.conflict.2.json", "student2");
        List<Feedback> feedbacks1 = database.loadAssessmentFomResources("test-data/model-assessment/assessment.conflict.1.json");
        List<Feedback> feedbacks2 = database.loadAssessmentFomResources("test-data/model-assessment/assessment.conflict.2.json");
        request.put("/api/modeling-submissions/" + submission1.getId() + "/feedback?submit=true", feedbacks1, HttpStatus.OK);
        List<Conflict> conflicts = request.putWithResponseBodyList("/api/modeling-submissions/" + submission2.getId() + "/feedback?submit=true", feedbacks2, Conflict.class,
                HttpStatus.CONFLICT);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findById(submission2.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacks(storedSubmission.getResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks2, storedResult.getFeedbacks());
        checkResultAfterSave(storedResult, assessor);
        assertThat(conflicts.size()).as("all three conflicts got detected").isEqualTo(3);
    }

    private Callable<Boolean> submissionHasBeenAssessed(Long id) {
        return () -> {
            Result result = modelingSubmissionRepo.findById(id).get().getResult();
            return result.getScore() != null;
        };
    }

    private void checkResultAfterSave(Result storedResult, User assessor) {
        assertThat(storedResult.isRated() == null || !storedResult.isRated()).as("rated has not been set").isTrue();
        assertThat(storedResult.getScore()).as("score hasnt been calculated").isNull();
        assertThat(storedResult.getAssessor().getId()).as("Assessor has been set").isEqualTo(assessor.getId());
        assertThat(storedResult.getResultString()).as("result string has not been set").isNull();
    }

    private void checkResultAfterSubmit(Result storedResult, User assessor) {
        assertThat(storedResult.isRated()).as("rated has been set").isTrue();
        assertThat(storedResult.getScore()).as("score has been calculated").isNotNull();
        assertThat(storedResult.getAssessor().getId()).as("Assessor has been set").isEqualTo(assessor.getId());
        assertThat(storedResult.getResultString()).as("result string has been set").isNotNull().isNotEqualTo("");
    }

    private void checkFeedbackCorrectlyStored(List<Feedback> sentFeedback, List<Feedback> storedFeedback) {
        assertThat(sentFeedback.size()).as("contains the same amount of feedback").isEqualTo(storedFeedback.size());
        Result storedFeedbackResult = new Result();
        Result sentFeedbackResult = new Result();
        storedFeedbackResult.setFeedbacks(storedFeedback);
        sentFeedbackResult.setFeedbacks(sentFeedback);
        storedFeedbackResult.evaluateFeedback(20);
        sentFeedbackResult.evaluateFeedback(20);
        assertThat(storedFeedbackResult.getScore()).as("stored feedback evaluates to the same score as sent feedback").isEqualTo(sentFeedbackResult.getScore());
        storedFeedback.forEach(feedback -> {
            assertThat(feedback.getType()).as("type has been set to MANUAL").isEqualTo(FeedbackType.MANUAL);
        });
    }
}
