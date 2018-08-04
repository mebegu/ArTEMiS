package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.GitlabException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("gitlab")
public class GitlabService implements VersionControlService {

    private final Logger log = LoggerFactory.getLogger(GitlabService.class);

    @Value("${artemis.gitlab.url}")
    private URL GITLAB_SERVER_URL;

    @Value("${artemis.gitlab.user}")
    private String GITLAB_USER;

    @Value("${artemis.gitlab.private-token}")
    private String GITLAB_PRIVATE_TOKEN;

    @Value("${artemis.lti.user-prefix}")
    private String USER_PREFIX = "";

    private final String API_PATH = "/api/v4/";

    private final UserService userService;

    public GitlabService(UserService userService) {
        this.userService = userService;
    }


    @Override
    public URL copyRepository(URL baseRepositoryUrl, String username) {
        Map<String, String> result = this.createRepository(getNamespaceFromUrl(baseRepositoryUrl), getProjectNameFromUrl(baseRepositoryUrl), username);
        try {
            return new URL(result.get("cloneUrl"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void configureRepository(URL repositoryUrl, String username) {
        if(username.startsWith(USER_PREFIX)) {
            // It is an automatically created user

            User user = userService.getUserByLogin(username).get();

            if (!userExists(username)) {
                log.debug("Gitlab user {} does not exist yet", username);
                String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
                createUser(username, userService.decryptPasswordByLogin(username).get(), user.getEmail(), displayName);

                // addUserToGroups(username, user.getGroups()); // TODO: check if we need this

            } else {
                log.debug("Gitlab user {} already exists", username);
            }
        }

        giveWritePermission(repositoryUrl, username);
    }

    @Override
    public void deleteRepository(URL repositoryUrl) {
        deleteRepositoryImpl(repositoryUrl);
    }

    @Override
    public URL getRepositoryWebUrl(Participation participation) {
        try {
            return new URL(GITLAB_SERVER_URL+
                "/" + getNamespaceFromUrl(participation.getRepositoryUrlAsUrl()) +
                "/" + getProjectNameFromUrl(participation.getRepositoryUrlAsUrl()) + "/tree/master"); // TODO: Don't hardcode master branch
        } catch (MalformedURLException e) {
            log.error("Couldn't construct repository web URL");
        }
        return GITLAB_SERVER_URL;
    }

    private String getNamespaceFromUrl(URL repositoryUrl) {
        // https://ga42xab@gitlabbruegge.in.tum.de/EIST2016RME/RMEXERCISE-ga42xab.git -> EIST2016RME
        String[] urlParts = repositoryUrl.getFile().split("/");
        if (urlParts.length > 1) {
            return urlParts[1];
        }
        return "";
    }

    private String getProjectNameFromUrl(URL repositoryUrl) {
        // https://ga42xab@gitlabbruegge.in.tum.de/EIST2016RME/RMEXERCISE-ga42xab.git -> RMEXERCISE-ga42xab
        String[] urlParts = repositoryUrl.getFile().split("/");
        if (urlParts.length > 2) {
            String repositoryName = urlParts[2];
            if (repositoryName.endsWith(".git")) {
                repositoryName = repositoryName.substring(0, repositoryName.length() - 4);
            }
            return repositoryName;
        }
        return "";
    }

    /**
     * This returns the URL encoded Identifier (consisting of the namespace and the projectname.
     *
     * @param repositoryUrl The repsitoryUrl
     * @return The already encoded Idenfifier
     */
    private String getURLEncodedIdentifier(URL repositoryUrl) {
        return getNamespaceFromUrl(repositoryUrl) + "%2F" + getProjectNameFromUrl(repositoryUrl);
    }

    /**
     * Uses the configured Gitlab account to create a new repository in the given namespace using the base repository.
     *
     * @param namespace          The namespace of the base project.
     * @param baseProjectName    The project name of the base repository.
     * @param username           The user for whom the repository is being forked.
     * @return The name of the forked repository
     * @throws GitlabException If the creation of the repository failed
     */
    private Map<String, String> createRepository(String namespace, String baseProjectName, String username) throws GitlabException {
        /*
         * In Gitlab, you cannot fork an existing repository with another name. Therefor we create a new repository and
         * specify the base repository as import_url. We authenticate with the private token and the according username.
         */
        // TODO: Check if we have to wait for a constant amount of time / check the import status as the import is done asynchronous by Gitlab
        String projectName = String.format("%s-%s", baseProjectName, username);
        Map<String, Object> body = new HashMap<>();
        body.put("name", projectName);
        body.put("namespace_id", getNamespaceId(namespace));
        body.put("import_url", GITLAB_SERVER_URL.getProtocol() + "://" + GITLAB_USER + ":" + GITLAB_PRIVATE_TOKEN + "@" +
            GITLAB_SERVER_URL.getAuthority() + GITLAB_SERVER_URL.getPath() + "/" + namespace + "/" + baseProjectName + ".git");

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + API_PATH + "projects/",
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                log.info("Repository already exists. Going to recover repository information...");
                Map<String, String> result = new HashMap<>();
                result.put("name", projectName);
                result.put("cloneUrl", buildCloneUrl(namespace, projectName, username).toString());
                return result;
            } else {
                throw e;
            }
        } catch (Exception e) {
            log.error("Could not fork base repository for user " + username, e);
            throw new GitlabException("Error while forking repository");
        }
        if (response != null && response.getStatusCode().equals(HttpStatus.CREATED)) {
            String name = (String) response.getBody().get("name");
            String cloneUrl = buildCloneUrl(namespace, projectName, username).toString();
            Map<String, String> result = new HashMap<>();
            result.put("name", name);
            result.put("cloneUrl", cloneUrl);
            return result;
        }
        return null;
    }

    /**
     * Creates an user on Gitlab
     *
     * @param username     The wanted Gitlab username
     * @param password     The wanted passowrd in clear text
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @throws GitlabException
     */
    public void createUser(String username, String password, String emailAddress, String displayName) throws GitlabException {
        String baseUrl = GITLAB_SERVER_URL + API_PATH + "users";
        Map<String, Object> body = new HashMap<>();
        body.put("email", emailAddress);
        body.put("username", username);
        body.put("password", password);
        body.put("name", displayName);
        body.put("skip_confirmation", true); // User should be able to login immediately

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        log.debug("Creating Gitlab user {} ({})", username, emailAddress);

        try {
            restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            log.error("Could not create Gitlab user " + username, e);
            throw new GitlabException("Error while creating user");
        }
    }

    /**
     * Gives user write permissions for a repository.
     *
     * @param repositoryUrl  The repository's URL.
     * @param username       The user whom to give write permissions.
     * @throws GitlabException
     */
    private void giveWritePermission(URL repositoryUrl, String username) throws GitlabException {
        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", getURLEncodedIdentifier(repositoryUrl), "/members");
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", getUserId(username, true));
        body.put("access_level", 30); // TODO: make this configurable? Access Level 30 equals Developer

        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                baseUri,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch(HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.CONFLICT)) {
                //TODO: Maybe check if the user has at least the access_level that would be given to him
                log.info("User already had permission. Assuming he has the correct permission.");
                return;
            }
            log.error("Could not give write permission", e);
            throw new GitlabException("Error while giving repository permissions");
        } catch (Exception e) {
            log.error("Could not give write permission", e);
            throw new GitlabException("Error while giving repository permissions");
        }
    }

    /**
     * Deletes the given repository from Gitlab.
     *
     * @param repositoryUrl    The repository's URL.
     */
    private void deleteRepositoryImpl(URL repositoryUrl) {
        URI baseUri = buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", getURLEncodedIdentifier(repositoryUrl));
        log.info("Delete repository " + baseUri);
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(baseUri, HttpMethod.DELETE, entity, Map.class);
        } catch (Exception e) {
            log.error("Could not delete repository", e);
        }
    }

    @Override
    public Boolean repositoryUrlIsValid(URL repositoryUrl) {
        String projectIdentifier = getURLEncodedIdentifier(repositoryUrl);
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(buildUri(GITLAB_SERVER_URL + API_PATH + "projects/", projectIdentifier), HttpMethod.GET, entity, Map.class);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Checks if an username exists on Gitlab
     *
     * @param username       The Gitlab username to check
     * @return true if it exists
     * @throws GitlabException if the user could not be requested
     */
    private Boolean userExists(String username) throws GitlabException {
        return getUserId(username, false) == -1;
    }

    /**
     * Get the user id of the given user.
     *
     * @param username       The username
     * @param expectExistence Whether the user is expected to be existing.
     * @return The user id of the given user, or -1 if no user exists and expectMissingUser is false
     * @throws GitlabException if the user id could not be requested OR the user does not exist but is expected to be existing.
     */
    private Integer getUserId(String username, boolean expectExistence) throws GitlabException {
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<List> response;
        try {
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + API_PATH + "users?username=" + username, //TODO: This seems to be undocumented, might break?
                HttpMethod.GET,
                entity,
                List.class);
        } catch (Exception e) {
            log.error("Error while getting user id of user " + username);
            throw new GitlabException("Error while getting user id", e);
        }

        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {
            if (response.getBody().size() == 0) {
                if (!expectExistence) { // User is not expected to be existing (this is for the userExists-method)
                    return -1; // User is not existing -> return -1
                }
                log.error("Error while getting user id of user {}: no user found", username);
                throw new GitlabException("Error while getting user id: No user found");
            }
            // TODO: maybe parse this into an extra object to avoid casts?
            LinkedHashMap<String, Object> user = (LinkedHashMap<String, Object>) response.getBody().get(0);
            return (Integer) user.get("id");
        }
        return null;
    }

    /**
     * Get the namespace id of the given namespace.
     *
     * @param namespace      The namespace
     * @return The id of the namespace
     * @throws GitlabException if the namespace id could not be requested.
     */
    private Integer getNamespaceId(String namespace) throws GitlabException {
        HttpHeaders headers = HeaderUtil.createPrivateTokenAuthorization(GITLAB_PRIVATE_TOKEN);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response;
        try {
            /**
             * In Gitlab, a namespace can either be an user or a group. As all participations of a specified exercise
             * should be in the same namespace, only a group-namespace is applicable.
             */
            // TODO: check whether we have to support user-namespaces too
            response = restTemplate.exchange(
                GITLAB_SERVER_URL + API_PATH + "groups/" + namespace,
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
                log.error("Error while getting namespace id of namespace " + namespace);
                throw new GitlabException("Error while getting namespace id", e);
        } catch (Exception e) {
            log.error("Error while getting namespace id of namespace " + namespace);
            throw new GitlabException("Error while getting namespace id", e);
        }
        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {
            return (int) response.getBody().get("id");
        }
        return null;
    }

    private URL buildCloneUrl(String namespace, String projectName, String username) {
        URL cloneUrl = null;
        try {
            cloneUrl = new URL(GITLAB_SERVER_URL.getProtocol() + "://" + username + "@" + GITLAB_SERVER_URL.getAuthority() + GITLAB_SERVER_URL.getPath() + "/" + namespace + "/" + projectName + ".git");
        } catch (MalformedURLException e) {
            log.error("Could not build clone URL", e);
        }
        return cloneUrl;
    }

    /**
     * Build an URI with already encoded components. No additional characters (like slashes are added in between the components.
     * This is needed as Gitlab uses URL-encoded namespaces, we therefor don't want the URL to be encoded twice.
     * This URI consists of 2 components, therefor the URL-encoded namespace is the last part of the URI.
     * Also see {@link #buildUri(String, String, String)}.
     *
     * @param url            The static part of the URI
     * @param parameter      The dynamic part of the URI
     * @return an URI containing url and parameter without additional encoding
     */
    private URI buildUri(String url, String parameter) {
        return buildUri(url, parameter, "");
    }

    /**
     * Build an URI with already encoded components. No additional characters (like slashes are added in between the components.
     * This is needed as Gitlab uses URL-encoded namespaces, we therefor don't want the URL to be encoded twice.
     * This URI consists of 3 components, therefor the URL-encoded namespace is the middle part of the URI,
     * and other static part is added afterwards. Also see {@link #buildUri(String, String)}.
     *
     * @param url            The static part of the URI
     * @param parameter      The dynamic part of the URI
     * @param staticParameter The additional static part after the dynamic parameter
     * @return an URI containing url, dynamic and static parameter without additional encoding
     */
    private URI buildUri(String url, String parameter, String staticParameter) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url).path(parameter).path(staticParameter);
        return builder.build(true).toUri();
    }
}