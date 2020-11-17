package io.github.jhipster.web.rest;

import static io.github.jhipster.web.rest.AccountResourceIT.TEST_USER_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.jhipster.JhipsterMinimalApp;
import io.github.jhipster.common.infrastructure.config.Constants;
import io.github.jhipster.security.AuthoritiesConstants;
import io.github.jhipster.user.application.UserApplicationService;
import io.github.jhipster.user.infrastructure.primary.dto.KeyAndPasswordDTO;
import io.github.jhipster.user.infrastructure.primary.dto.ManagedUserDTO;
import io.github.jhipster.user.infrastructure.primary.dto.PasswordChangeDTO;
import io.github.jhipster.user.infrastructure.primary.dto.UserDTO;
import io.github.jhipster.user.infrastructure.primary.rest.AccountResource;
import io.github.jhipster.user.infrastructure.secondary.AuthorityRepository;
import io.github.jhipster.user.infrastructure.secondary.UserEntity;
import io.github.jhipster.user.infrastructure.secondary.UserRepository;
import java.time.Instant;
import java.util.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link AccountResource} REST controller.
 */
@AutoConfigureMockMvc
@WithMockUser(value = TEST_USER_LOGIN)
@SpringBootTest(classes = JhipsterMinimalApp.class)
class AccountResourceIT {

    static final String TEST_USER_LOGIN = "test";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private UserApplicationService userApplicationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc restAccountMockMvc;

    @Test
    @WithUnauthenticatedMockUser
    void testNonAuthenticatedUser() throws Exception {
        restAccountMockMvc
            .perform(get("/api/authenticate").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    @Test
    void testAuthenticatedUser() throws Exception {
        restAccountMockMvc
            .perform(
                get("/api/authenticate")
                    .with(
                        request -> {
                            request.setRemoteUser(TEST_USER_LOGIN);
                            return request;
                        }
                    )
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(content().string(TEST_USER_LOGIN));
    }

    @Test
    void testGetExistingAccount() throws Exception {
        Set<String> authorities = new HashSet<>();
        authorities.add(AuthoritiesConstants.ADMIN);

        UserDTO user = new UserDTO();
        user.setLogin(TEST_USER_LOGIN);
        user.setFirstName("john");
        user.setLastName("doe");
        user.setEmail("john.doe@jhipster.com");
        user.setImageUrl("http://placehold.it/50x50");
        user.setLangKey("en");
        user.setAuthorities(authorities);
        userApplicationService.createUser(user);

        restAccountMockMvc
            .perform(get("/api/account").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.login").value(TEST_USER_LOGIN))
            .andExpect(jsonPath("$.firstName").value("john"))
            .andExpect(jsonPath("$.lastName").value("doe"))
            .andExpect(jsonPath("$.email").value("john.doe@jhipster.com"))
            .andExpect(jsonPath("$.imageUrl").value("http://placehold.it/50x50"))
            .andExpect(jsonPath("$.langKey").value("en"))
            .andExpect(jsonPath("$.authorities").value(AuthoritiesConstants.ADMIN));
    }

    @Test
    void testGetUnknownAccount() throws Exception {
        restAccountMockMvc
            .perform(get("/api/account").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @Transactional
    void testRegisterValid() throws Exception {
        ManagedUserDTO validUser = new ManagedUserDTO();
        validUser.setLogin("test-register-valid");
        validUser.setPassword("password");
        validUser.setFirstName("Alice");
        validUser.setLastName("Test");
        validUser.setEmail("test-register-valid@example.com");
        validUser.setImageUrl("http://placehold.it/50x50");
        validUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        validUser.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));
        assertThat(userRepository.findOneByLogin("test-register-valid")).isEmpty();

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(validUser)))
            .andExpect(status().isCreated());

        assertThat(userRepository.findOneByLogin("test-register-valid")).isPresent();
    }

    @Test
    @Transactional
    void testRegisterInvalidLogin() throws Exception {
        ManagedUserDTO invalidUser = new ManagedUserDTO();
        invalidUser.setLogin("funky-log(n"); // <-- invalid
        invalidUser.setPassword("password");
        invalidUser.setFirstName("Funky");
        invalidUser.setLastName("One");
        invalidUser.setEmail("funky@example.com");
        invalidUser.setActivated(true);
        invalidUser.setImageUrl("http://placehold.it/50x50");
        invalidUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        invalidUser.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(invalidUser)))
            .andExpect(status().isBadRequest());

        Optional<UserEntity> user = userRepository.findOneByEmailIgnoreCase("funky@example.com");
        assertThat(user).isEmpty();
    }

    @Test
    @Transactional
    void testRegisterInvalidEmail() throws Exception {
        ManagedUserDTO invalidUser = new ManagedUserDTO();
        invalidUser.setLogin("bob");
        invalidUser.setPassword("password");
        invalidUser.setFirstName("Bob");
        invalidUser.setLastName("Green");
        invalidUser.setEmail("invalid"); // <-- invalid
        invalidUser.setActivated(true);
        invalidUser.setImageUrl("http://placehold.it/50x50");
        invalidUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        invalidUser.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(invalidUser)))
            .andExpect(status().isBadRequest());

        Optional<UserEntity> user = userRepository.findOneByLogin("bob");
        assertThat(user).isEmpty();
    }

    @Test
    @Transactional
    void testRegisterInvalidPassword() throws Exception {
        ManagedUserDTO invalidUser = new ManagedUserDTO();
        invalidUser.setLogin("bob");
        invalidUser.setPassword("123"); // password with only 3 digits
        invalidUser.setFirstName("Bob");
        invalidUser.setLastName("Green");
        invalidUser.setEmail("bob@example.com");
        invalidUser.setActivated(true);
        invalidUser.setImageUrl("http://placehold.it/50x50");
        invalidUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        invalidUser.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(invalidUser)))
            .andExpect(status().isBadRequest());

        Optional<UserEntity> user = userRepository.findOneByLogin("bob");
        assertThat(user).isEmpty();
    }

    @Test
    @Transactional
    void testRegisterNullPassword() throws Exception {
        ManagedUserDTO invalidUser = new ManagedUserDTO();
        invalidUser.setLogin("bob");
        invalidUser.setPassword(null); // invalid null password
        invalidUser.setFirstName("Bob");
        invalidUser.setLastName("Green");
        invalidUser.setEmail("bob@example.com");
        invalidUser.setActivated(true);
        invalidUser.setImageUrl("http://placehold.it/50x50");
        invalidUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        invalidUser.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(invalidUser)))
            .andExpect(status().isBadRequest());

        Optional<UserEntity> user = userRepository.findOneByLogin("bob");
        assertThat(user).isEmpty();
    }

    @Test
    @Transactional
    void testRegisterDuplicateLogin() throws Exception {
        // First registration
        ManagedUserDTO firstUser = new ManagedUserDTO();
        firstUser.setLogin("alice");
        firstUser.setPassword("password");
        firstUser.setFirstName("Alice");
        firstUser.setLastName("Something");
        firstUser.setEmail("alice@example.com");
        firstUser.setImageUrl("http://placehold.it/50x50");
        firstUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        firstUser.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        // Duplicate login, different email
        ManagedUserDTO secondUser = new ManagedUserDTO();
        secondUser.setLogin(firstUser.getLogin());
        secondUser.setPassword(firstUser.getPassword());
        secondUser.setFirstName(firstUser.getFirstName());
        secondUser.setLastName(firstUser.getLastName());
        secondUser.setEmail("alice2@example.com");
        secondUser.setImageUrl(firstUser.getImageUrl());
        secondUser.setLangKey(firstUser.getLangKey());
        secondUser.setCreatedBy(firstUser.getCreatedBy());
        secondUser.setCreatedDate(firstUser.getCreatedDate());
        secondUser.setLastModifiedBy(firstUser.getLastModifiedBy());
        secondUser.setLastModifiedDate(firstUser.getLastModifiedDate());
        secondUser.setAuthorities(new HashSet<>(firstUser.getAuthorities()));

        // First user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(firstUser)))
            .andExpect(status().isCreated());

        // Second (non activated) user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(secondUser)))
            .andExpect(status().isCreated());

        Optional<UserEntity> testUser = userRepository.findOneByEmailIgnoreCase("alice2@example.com");
        assertThat(testUser).isPresent();
        testUser.get().setActivated(true);
        userRepository.save(testUser.get());

        // Second (already activated) user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(secondUser)))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Transactional
    void testRegisterDuplicateEmail() throws Exception {
        // First user
        ManagedUserDTO firstUser = new ManagedUserDTO();
        firstUser.setLogin("test-register-duplicate-email");
        firstUser.setPassword("password");
        firstUser.setFirstName("Alice");
        firstUser.setLastName("Test");
        firstUser.setEmail("test-register-duplicate-email@example.com");
        firstUser.setImageUrl("http://placehold.it/50x50");
        firstUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        firstUser.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        // Register first user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(firstUser)))
            .andExpect(status().isCreated());

        Optional<UserEntity> testUser1 = userRepository.findOneByLogin("test-register-duplicate-email");
        assertThat(testUser1).isPresent();

        // Duplicate email, different login
        ManagedUserDTO secondUser = new ManagedUserDTO();
        secondUser.setLogin("test-register-duplicate-email-2");
        secondUser.setPassword(firstUser.getPassword());
        secondUser.setFirstName(firstUser.getFirstName());
        secondUser.setLastName(firstUser.getLastName());
        secondUser.setEmail(firstUser.getEmail());
        secondUser.setImageUrl(firstUser.getImageUrl());
        secondUser.setLangKey(firstUser.getLangKey());
        secondUser.setAuthorities(new HashSet<>(firstUser.getAuthorities()));

        // Register second (non activated) user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(secondUser)))
            .andExpect(status().isCreated());

        Optional<UserEntity> testUser2 = userRepository.findOneByLogin("test-register-duplicate-email");
        assertThat(testUser2).isEmpty();

        Optional<UserEntity> testUser3 = userRepository.findOneByLogin("test-register-duplicate-email-2");
        assertThat(testUser3).isPresent();

        // Duplicate email - with uppercase email address
        ManagedUserDTO userWithUpperCaseEmail = new ManagedUserDTO();
        userWithUpperCaseEmail.setId(firstUser.getId());
        userWithUpperCaseEmail.setLogin("test-register-duplicate-email-3");
        userWithUpperCaseEmail.setPassword(firstUser.getPassword());
        userWithUpperCaseEmail.setFirstName(firstUser.getFirstName());
        userWithUpperCaseEmail.setLastName(firstUser.getLastName());
        userWithUpperCaseEmail.setEmail("TEST-register-duplicate-email@example.com");
        userWithUpperCaseEmail.setImageUrl(firstUser.getImageUrl());
        userWithUpperCaseEmail.setLangKey(firstUser.getLangKey());
        userWithUpperCaseEmail.setAuthorities(new HashSet<>(firstUser.getAuthorities()));

        // Register third (not activated) user
        restAccountMockMvc
            .perform(
                post("/api/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(userWithUpperCaseEmail))
            )
            .andExpect(status().isCreated());

        Optional<UserEntity> testUser4 = userRepository.findOneByLogin("test-register-duplicate-email-3");
        assertThat(testUser4).isPresent();
        assertThat(testUser4.get().getEmail()).isEqualTo("test-register-duplicate-email@example.com");

        testUser4.get().setActivated(true);
        userApplicationService.updateUser((new UserDTO(testUser4.get())));

        // Register 4th (already activated) user
        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(secondUser)))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Transactional
    void testRegisterAdminIsIgnored() throws Exception {
        ManagedUserDTO validUser = new ManagedUserDTO();
        validUser.setLogin("badguy");
        validUser.setPassword("password");
        validUser.setFirstName("Bad");
        validUser.setLastName("Guy");
        validUser.setEmail("badguy@example.com");
        validUser.setActivated(true);
        validUser.setImageUrl("http://placehold.it/50x50");
        validUser.setLangKey(Constants.DEFAULT_LANGUAGE);
        validUser.setAuthorities(Collections.singleton(AuthoritiesConstants.ADMIN));

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(validUser)))
            .andExpect(status().isCreated());

        Optional<UserEntity> userDup = userRepository.findOneWithAuthoritiesByLogin("badguy");
        assertThat(userDup).isPresent();
        assertThat(userDup.get().getAuthorities())
            .hasSize(1)
            .containsExactly(authorityRepository.findById(AuthoritiesConstants.USER).get());
    }

    @Test
    @Transactional
    void testActivateAccount() throws Exception {
        final String activationKey = "some activation key";
        UserEntity userEntity = new UserEntity();
        userEntity.setLogin("activate-account");
        userEntity.setEmail("activate-account@example.com");
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setActivated(false);
        userEntity.setActivationKey(activationKey);

        userRepository.saveAndFlush(userEntity);

        restAccountMockMvc.perform(get("/api/activate?key={activationKey}", activationKey)).andExpect(status().isOk());

        userEntity = userRepository.findOneByLogin(userEntity.getLogin()).orElse(null);
        assertThat(userEntity.isActivated()).isTrue();
    }

    @Test
    @Transactional
    void testActivateAccountWithWrongKey() throws Exception {
        restAccountMockMvc.perform(get("/api/activate?key=wrongActivationKey")).andExpect(status().isInternalServerError());
    }

    @Test
    @Transactional
    @WithMockUser("save-account")
    void testSaveAccount() throws Exception {
        UserEntity userEntity = new UserEntity();
        userEntity.setLogin("save-account");
        userEntity.setEmail("save-account@example.com");
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setActivated(true);
        userRepository.saveAndFlush(userEntity);

        UserDTO userDTO = new UserDTO();
        userDTO.setLogin("not-used");
        userDTO.setFirstName("firstname");
        userDTO.setLastName("lastname");
        userDTO.setEmail("save-account@example.com");
        userDTO.setActivated(false);
        userDTO.setImageUrl("http://placehold.it/50x50");
        userDTO.setLangKey(Constants.DEFAULT_LANGUAGE);
        userDTO.setAuthorities(Collections.singleton(AuthoritiesConstants.ADMIN));

        restAccountMockMvc
            .perform(post("/api/account").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(userDTO)))
            .andExpect(status().isOk());

        UserEntity updatedUserEntity = userRepository.findOneWithAuthoritiesByLogin(userEntity.getLogin()).orElse(null);
        assertThat(updatedUserEntity.getFirstName()).isEqualTo(userDTO.getFirstName());
        assertThat(updatedUserEntity.getLastName()).isEqualTo(userDTO.getLastName());
        assertThat(updatedUserEntity.getEmail()).isEqualTo(userDTO.getEmail());
        assertThat(updatedUserEntity.getLangKey()).isEqualTo(userDTO.getLangKey());
        assertThat(updatedUserEntity.getPassword()).isEqualTo(userEntity.getPassword());
        assertThat(updatedUserEntity.getImageUrl()).isEqualTo(userDTO.getImageUrl());
        assertThat(updatedUserEntity.isActivated()).isTrue();
        assertThat(updatedUserEntity.getAuthorities()).isEmpty();
    }

    @Test
    @Transactional
    @WithMockUser("save-invalid-email")
    void testSaveInvalidEmail() throws Exception {
        UserEntity userEntity = new UserEntity();
        userEntity.setLogin("save-invalid-email");
        userEntity.setEmail("save-invalid-email@example.com");
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setActivated(true);

        userRepository.saveAndFlush(userEntity);

        UserDTO userDTO = new UserDTO();
        userDTO.setLogin("not-used");
        userDTO.setFirstName("firstname");
        userDTO.setLastName("lastname");
        userDTO.setEmail("invalid email");
        userDTO.setActivated(false);
        userDTO.setImageUrl("http://placehold.it/50x50");
        userDTO.setLangKey(Constants.DEFAULT_LANGUAGE);
        userDTO.setAuthorities(Collections.singleton(AuthoritiesConstants.ADMIN));

        restAccountMockMvc
            .perform(post("/api/account").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(userDTO)))
            .andExpect(status().isBadRequest());

        assertThat(userRepository.findOneByEmailIgnoreCase("invalid email")).isNotPresent();
    }

    @Test
    @Transactional
    @WithMockUser("save-existing-email")
    void testSaveExistingEmail() throws Exception {
        UserEntity userEntity = new UserEntity();
        userEntity.setLogin("save-existing-email");
        userEntity.setEmail("save-existing-email@example.com");
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setActivated(true);
        userRepository.saveAndFlush(userEntity);

        UserEntity anotherUserEntity = new UserEntity();
        anotherUserEntity.setLogin("save-existing-email2");
        anotherUserEntity.setEmail("save-existing-email2@example.com");
        anotherUserEntity.setPassword(RandomStringUtils.random(60));
        anotherUserEntity.setActivated(true);

        userRepository.saveAndFlush(anotherUserEntity);

        UserDTO userDTO = new UserDTO();
        userDTO.setLogin("not-used");
        userDTO.setFirstName("firstname");
        userDTO.setLastName("lastname");
        userDTO.setEmail("save-existing-email2@example.com");
        userDTO.setActivated(false);
        userDTO.setImageUrl("http://placehold.it/50x50");
        userDTO.setLangKey(Constants.DEFAULT_LANGUAGE);
        userDTO.setAuthorities(Collections.singleton(AuthoritiesConstants.ADMIN));

        restAccountMockMvc
            .perform(post("/api/account").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(userDTO)))
            .andExpect(status().isBadRequest());

        UserEntity updatedUserEntity = userRepository.findOneByLogin("save-existing-email").orElse(null);
        assertThat(updatedUserEntity.getEmail()).isEqualTo("save-existing-email@example.com");
    }

    @Test
    @Transactional
    @WithMockUser("save-existing-email-and-login")
    void testSaveExistingEmailAndLogin() throws Exception {
        UserEntity userEntity = new UserEntity();
        userEntity.setLogin("save-existing-email-and-login");
        userEntity.setEmail("save-existing-email-and-login@example.com");
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setActivated(true);
        userRepository.saveAndFlush(userEntity);

        UserDTO userDTO = new UserDTO();
        userDTO.setLogin("not-used");
        userDTO.setFirstName("firstname");
        userDTO.setLastName("lastname");
        userDTO.setEmail("save-existing-email-and-login@example.com");
        userDTO.setActivated(false);
        userDTO.setImageUrl("http://placehold.it/50x50");
        userDTO.setLangKey(Constants.DEFAULT_LANGUAGE);
        userDTO.setAuthorities(Collections.singleton(AuthoritiesConstants.ADMIN));

        restAccountMockMvc
            .perform(post("/api/account").contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(userDTO)))
            .andExpect(status().isOk());

        UserEntity updatedUserEntity = userRepository.findOneByLogin("save-existing-email-and-login").orElse(null);
        assertThat(updatedUserEntity.getEmail()).isEqualTo("save-existing-email-and-login@example.com");
    }

    @Test
    @Transactional
    @WithMockUser("change-password-wrong-existing-password")
    void testChangePasswordWrongExistingPassword() throws Exception {
        UserEntity userEntity = new UserEntity();
        String currentPassword = RandomStringUtils.random(60);
        userEntity.setPassword(passwordEncoder.encode(currentPassword));
        userEntity.setLogin("change-password-wrong-existing-password");
        userEntity.setEmail("change-password-wrong-existing-password@example.com");
        userRepository.saveAndFlush(userEntity);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO("1" + currentPassword, "new password")))
            )
            .andExpect(status().isBadRequest());

        UserEntity updatedUserEntity = userRepository.findOneByLogin("change-password-wrong-existing-password").orElse(null);
        assertThat(passwordEncoder.matches("new password", updatedUserEntity.getPassword())).isFalse();
        assertThat(passwordEncoder.matches(currentPassword, updatedUserEntity.getPassword())).isTrue();
    }

    @Test
    @Transactional
    @WithMockUser("change-password")
    void testChangePassword() throws Exception {
        UserEntity userEntity = new UserEntity();
        String currentPassword = RandomStringUtils.random(60);
        userEntity.setPassword(passwordEncoder.encode(currentPassword));
        userEntity.setLogin("change-password");
        userEntity.setEmail("change-password@example.com");
        userRepository.saveAndFlush(userEntity);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO(currentPassword, "new password")))
            )
            .andExpect(status().isOk());

        UserEntity updatedUserEntity = userRepository.findOneByLogin("change-password").orElse(null);
        assertThat(passwordEncoder.matches("new password", updatedUserEntity.getPassword())).isTrue();
    }

    @Test
    @Transactional
    @WithMockUser("change-password-too-small")
    void testChangePasswordTooSmall() throws Exception {
        UserEntity userEntity = new UserEntity();
        String currentPassword = RandomStringUtils.random(60);
        userEntity.setPassword(passwordEncoder.encode(currentPassword));
        userEntity.setLogin("change-password-too-small");
        userEntity.setEmail("change-password-too-small@example.com");
        userRepository.saveAndFlush(userEntity);

        String newPassword = RandomStringUtils.random(ManagedUserDTO.PASSWORD_MIN_LENGTH - 1);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO(currentPassword, newPassword)))
            )
            .andExpect(status().isBadRequest());

        UserEntity updatedUserEntity = userRepository.findOneByLogin("change-password-too-small").orElse(null);
        assertThat(updatedUserEntity.getPassword()).isEqualTo(userEntity.getPassword());
    }

    @Test
    @Transactional
    @WithMockUser("change-password-too-long")
    void testChangePasswordTooLong() throws Exception {
        UserEntity userEntity = new UserEntity();
        String currentPassword = RandomStringUtils.random(60);
        userEntity.setPassword(passwordEncoder.encode(currentPassword));
        userEntity.setLogin("change-password-too-long");
        userEntity.setEmail("change-password-too-long@example.com");
        userRepository.saveAndFlush(userEntity);

        String newPassword = RandomStringUtils.random(ManagedUserDTO.PASSWORD_MAX_LENGTH + 1);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO(currentPassword, newPassword)))
            )
            .andExpect(status().isBadRequest());

        UserEntity updatedUserEntity = userRepository.findOneByLogin("change-password-too-long").orElse(null);
        assertThat(updatedUserEntity.getPassword()).isEqualTo(userEntity.getPassword());
    }

    @Test
    @Transactional
    @WithMockUser("change-password-empty")
    void testChangePasswordEmpty() throws Exception {
        UserEntity userEntity = new UserEntity();
        String currentPassword = RandomStringUtils.random(60);
        userEntity.setPassword(passwordEncoder.encode(currentPassword));
        userEntity.setLogin("change-password-empty");
        userEntity.setEmail("change-password-empty@example.com");
        userRepository.saveAndFlush(userEntity);

        restAccountMockMvc
            .perform(
                post("/api/account/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(new PasswordChangeDTO(currentPassword, "")))
            )
            .andExpect(status().isBadRequest());

        UserEntity updatedUserEntity = userRepository.findOneByLogin("change-password-empty").orElse(null);
        assertThat(updatedUserEntity.getPassword()).isEqualTo(userEntity.getPassword());
    }

    @Test
    @Transactional
    void testRequestPasswordReset() throws Exception {
        UserEntity userEntity = new UserEntity();
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setActivated(true);
        userEntity.setLogin("password-reset");
        userEntity.setEmail("password-reset@example.com");
        userRepository.saveAndFlush(userEntity);

        restAccountMockMvc
            .perform(post("/api/account/reset-password/init").content("password-reset@example.com"))
            .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void testRequestPasswordResetUpperCaseEmail() throws Exception {
        UserEntity userEntity = new UserEntity();
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setActivated(true);
        userEntity.setLogin("password-reset-upper-case");
        userEntity.setEmail("password-reset-upper-case@example.com");
        userRepository.saveAndFlush(userEntity);

        restAccountMockMvc
            .perform(post("/api/account/reset-password/init").content("password-reset-upper-case@EXAMPLE.COM"))
            .andExpect(status().isOk());
    }

    @Test
    void testRequestPasswordResetWrongEmail() throws Exception {
        restAccountMockMvc
            .perform(post("/api/account/reset-password/init").content("password-reset-wrong-email@example.com"))
            .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void testFinishPasswordReset() throws Exception {
        UserEntity userEntity = new UserEntity();
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setLogin("finish-password-reset");
        userEntity.setEmail("finish-password-reset@example.com");
        userEntity.setResetDate(Instant.now().plusSeconds(60));
        userEntity.setResetKey("reset key");
        userRepository.saveAndFlush(userEntity);

        KeyAndPasswordDTO keyAndPassword = new KeyAndPasswordDTO();
        keyAndPassword.setKey(userEntity.getResetKey());
        keyAndPassword.setNewPassword("new password");

        restAccountMockMvc
            .perform(
                post("/api/account/reset-password/finish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(keyAndPassword))
            )
            .andExpect(status().isOk());

        UserEntity updatedUserEntity = userRepository.findOneByLogin(userEntity.getLogin()).orElse(null);
        assertThat(passwordEncoder.matches(keyAndPassword.getNewPassword(), updatedUserEntity.getPassword())).isTrue();
    }

    @Test
    @Transactional
    void testFinishPasswordResetTooSmall() throws Exception {
        UserEntity userEntity = new UserEntity();
        userEntity.setPassword(RandomStringUtils.random(60));
        userEntity.setLogin("finish-password-reset-too-small");
        userEntity.setEmail("finish-password-reset-too-small@example.com");
        userEntity.setResetDate(Instant.now().plusSeconds(60));
        userEntity.setResetKey("reset key too small");
        userRepository.saveAndFlush(userEntity);

        KeyAndPasswordDTO keyAndPassword = new KeyAndPasswordDTO();
        keyAndPassword.setKey(userEntity.getResetKey());
        keyAndPassword.setNewPassword("foo");

        restAccountMockMvc
            .perform(
                post("/api/account/reset-password/finish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(keyAndPassword))
            )
            .andExpect(status().isBadRequest());

        UserEntity updatedUserEntity = userRepository.findOneByLogin(userEntity.getLogin()).orElse(null);
        assertThat(passwordEncoder.matches(keyAndPassword.getNewPassword(), updatedUserEntity.getPassword())).isFalse();
    }

    @Test
    @Transactional
    void testFinishPasswordResetWrongKey() throws Exception {
        KeyAndPasswordDTO keyAndPassword = new KeyAndPasswordDTO();
        keyAndPassword.setKey("wrong reset key");
        keyAndPassword.setNewPassword("new password");

        restAccountMockMvc
            .perform(
                post("/api/account/reset-password/finish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(keyAndPassword))
            )
            .andExpect(status().isInternalServerError());
    }
}
