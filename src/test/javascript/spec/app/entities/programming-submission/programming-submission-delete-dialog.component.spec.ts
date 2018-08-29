/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { ProgrammingSubmissionDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/programming-submission/programming-submission-delete-dialog.component';
import { ProgrammingSubmissionService } from '../../../../../../main/webapp/app/entities/programming-submission/programming-submission.service';

describe('Component Tests', () => {

    describe('ProgrammingSubmission Management Delete Component', () => {
        let comp: ProgrammingSubmissionDeleteDialogComponent;
        let fixture: ComponentFixture<ProgrammingSubmissionDeleteDialogComponent>;
        let service: ProgrammingSubmissionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ProgrammingSubmissionDeleteDialogComponent],
                providers: [
                    ProgrammingSubmissionService
                ]
            })
            .overrideTemplate(ProgrammingSubmissionDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ProgrammingSubmissionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingSubmissionService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        spyOn(service, 'delete').and.returnValue(Observable.of({}));

                        // WHEN
                        comp.confirmDelete(123);
                        tick();

                        // THEN
                        expect(service.delete).toHaveBeenCalledWith(123);
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});