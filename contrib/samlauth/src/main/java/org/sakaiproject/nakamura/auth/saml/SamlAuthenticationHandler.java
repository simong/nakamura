/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.auth.saml;

import com.google.common.collect.Lists;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.auth.core.spi.AuthenticationFeedbackHandler;
import org.apache.sling.auth.core.spi.AuthenticationHandler;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.auth.core.spi.DefaultAuthenticationFeedbackHandler;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.sling.servlets.post.ModificationType;
import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.SignableSAMLObject;
import org.opensaml.common.binding.BasicSAMLMessageContext;
import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.binding.decoding.BasicURLComparator;
import org.opensaml.common.binding.decoding.URIComparator;
import org.opensaml.saml2.binding.decoding.HTTPPostDecoder;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.AudienceRestriction;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.ws.message.decoder.MessageDecodingException;
import org.opensaml.ws.transport.http.HttpServletRequestAdapter;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.security.SecurityException;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.validation.ValidationException;
import org.osgi.framework.Constants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.SimpleCredentials;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This class integrates SSO with the Sling authentication framework. The integration is
 * needed only due to limitations on servlet filter support in the OSGi / Sling
 * environment.
 */
@Component(immediate = true, metatype = true, policy = ConfigurationPolicy.REQUIRE)
@Service({SamlAuthenticationHandler.class, AuthenticationHandler.class, AuthenticationFeedbackHandler.class})
@Properties({
  @Property(name = Constants.SERVICE_RANKING, intValue = -5),
  @Property(name = AuthenticationHandler.PATH_PROPERTY, value = "/"),
  @Property(name = AuthenticationHandler.TYPE_PROPERTY, value = SamlAuthenticationHandler.AUTH_TYPE, propertyPrivate = true)
})
public class SamlAuthenticationHandler implements AuthenticationHandler,
    AuthenticationFeedbackHandler {

  public static final String AUTH_TYPE = "SAML";

  private static final Logger LOGGER = LoggerFactory
      .getLogger(SamlAuthenticationHandler.class);

  /**
   * URI comparator that always returns true. Useful for development and testing.
   */
  protected static final URIComparator TRUE_URI_COMPARATOR = new URIComparator() {
    public boolean compare(String uri1, String uri2) {
      return true;
    }
  };

  /**
   * URI comparator that matches the URI up to the query string (excludes ? and beyond).
   */
  protected static final URIComparator SIMPLE_URI_COMPARATOR = new URIComparator() {
    public boolean compare(String uri1, String uri2) {
      String u1 = (uri1.indexOf('?') >= 0) ? uri1.substring(0, uri1.indexOf('?')) : uri1;
      String u2 = (uri2.indexOf('?') >= 0) ? uri2.substring(0, uri2.indexOf('?')) : uri2;
      return u1.equals(u2);
    }
  };

  protected static final URIComparator HOST_ONLY_URI_COMPARATOR = new URIComparator() {
    public boolean compare(String uri1, String uri2) {
      try {
        URI urii1 = new URI(uri1);
        URI urii2 = new URI(uri2);
        return urii1.getHost().equals(urii2.getHost());
      } catch (URISyntaxException e) {
        return false;
      }
    }
  };

  /**
   * Represents the constant for where the assertion will be located in memory.
   */
  static final String AUTHN_INFO = "org.sakaiproject.nakamura.auth.saml.SamlAuthnInfo";

  static final boolean DEFAULT_SSO_AUTOCREATE_USER = false;
  @Property(boolValue = SamlAuthenticationHandler.DEFAULT_SSO_AUTOCREATE_USER)
  static final String SSO_AUTOCREATE_USER = "sakai.auth.saml.user.autocreate";
  private boolean autoCreateUser;

  static final String DEFAULT_ENTITY_ID_LABEL = "spEntityID";
  @Property(SamlAuthenticationHandler.DEFAULT_ENTITY_ID_LABEL)
  static final String ENTITY_ID_LABEL = "sakai.auth.saml.entityid.label";
  private String entityIdLabel;

  static final String DEFAULT_ENTITY_ID = "http://localhost/system/sling/samlauth/login";
  @Property(SamlAuthenticationHandler.DEFAULT_ENTITY_ID)
  static final String ENTITY_ID = "sakai.auth.saml.entityid";
  private String entityId;

  @Property
  static final String CERTIFICATE = "sakai.auth.saml.certificate";
  private X509Certificate certificate;

  @Property
  static final String LOGOUT_URL = "sakai.auth.saml.url.logout";
  private String logoutUrl;

  @Property
  static final String SERVER_URL = "sakai.auth.saml.url.server";
  private String serverUrl;

  static final String DEFAULT_MISSING_LOCAL_USER_URL = "/dev/500.html";
  @Property(SamlAuthenticationHandler.DEFAULT_MISSING_LOCAL_USER_URL)
  static final String MISSING_LOCAL_USER_URL = "sakai.auth.saml.user.missing";
  private String missingLocalUserUrl;

  static final boolean DEFAULT_UPDATE_ATTRS_ON_LOGIN = true;
  @Property(boolValue = SamlAuthenticationHandler.DEFAULT_UPDATE_ATTRS_ON_LOGIN)
  static final String UPDATE_ATTRS_ON_LOGIN = "sakai.auth.saml.user.attrs.update";
  private boolean updateAttrsOnLogin;

  static final String MATCH_DESTINATION_NONE = "none";
  static final String MATCH_DESTINATION_HOST = "host";
  static final String MATCH_DESTINATION_SIMPLE = "simple";
  static final String MATCH_DESTINATION_CANONICAL = "canonical";
  static final String DEFAULT_MATCH_DESTINATION = MATCH_DESTINATION_SIMPLE;
  @Property(value = SamlAuthenticationHandler.DEFAULT_MATCH_DESTINATION, options = {
    @PropertyOption(name = SamlAuthenticationHandler.MATCH_DESTINATION_NONE, value = SamlAuthenticationHandler.MATCH_DESTINATION_NONE),
    @PropertyOption(name = SamlAuthenticationHandler.MATCH_DESTINATION_HOST, value = SamlAuthenticationHandler.MATCH_DESTINATION_HOST),
    @PropertyOption(name = SamlAuthenticationHandler.MATCH_DESTINATION_SIMPLE, value = SamlAuthenticationHandler.MATCH_DESTINATION_SIMPLE),
    @PropertyOption(name = SamlAuthenticationHandler.MATCH_DESTINATION_CANONICAL, value = SamlAuthenticationHandler.MATCH_DESTINATION_CANONICAL)
  })
  static final String MATCH_DESTINATION = "sakai.auth.saml.destination.match";
  private String matchDestination;

  static final String SIGNATURE_ASSERTION = "assertion";
  static final String SIGNATURE_MESSAGE = "message";
  static final String DEFAULT_SIGNATURE_LOCATION = SamlAuthenticationHandler.SIGNATURE_MESSAGE;
  @Property(value = SamlAuthenticationHandler.DEFAULT_SIGNATURE_LOCATION, options = {
    @PropertyOption(name = SamlAuthenticationHandler.SIGNATURE_ASSERTION, value = SamlAuthenticationHandler.SIGNATURE_ASSERTION),
    @PropertyOption(name = SamlAuthenticationHandler.SIGNATURE_MESSAGE, value = SamlAuthenticationHandler.SIGNATURE_MESSAGE)
  })
  static final String SIGNATURE_LOCATION = "sakai.auth.saml.signature.location";
  private String signatureLocation;

  // needed for the automatic user creation.
  @Reference
  protected Repository repository;

  @Reference
  protected LiteAuthorizablePostProcessService authzPostProcessService;

  private SAMLSignatureProfileValidator profileValidator;
  private String loginUrl;
  private HTTPPostDecoder decoder;
  private URIComparator uriComparator;

  /**
   * Define the set of authentication-related query parameters which should be removed
   * from the "service" URL sent to the SSO server.
   */
  Set<String> filteredQueryStrings = new HashSet<String>(
      Arrays.asList(REQUEST_LOGIN_PARAMETER));

  public SamlAuthenticationHandler() {
    try {
      DefaultBootstrap.bootstrap();
    } catch (ConfigurationException e) {
      throw new RuntimeException(e.getMessage(), e);
    }

    BasicParserPool parserPool = new BasicParserPool();

    decoder = new HTTPPostDecoder(parserPool);
    profileValidator = new SAMLSignatureProfileValidator();
  }

  SamlAuthenticationHandler(Repository repository,
      LiteAuthorizablePostProcessService authzPostProcessService) {
    this();
    this.repository = repository;
    this.authzPostProcessService = authzPostProcessService;
  }

  // ----------- OSGi integration ----------------------------
  @Activate @Modified
  protected void activate(Map<?, ?> props) throws Exception {
    serverUrl = PropertiesUtil.toString(props.get(SERVER_URL), null);
    entityIdLabel = PropertiesUtil.toString(props.get(ENTITY_ID_LABEL), DEFAULT_ENTITY_ID_LABEL);
    entityId = PropertiesUtil.toString(props.get(ENTITY_ID), DEFAULT_ENTITY_ID);
    logoutUrl = PropertiesUtil.toString(props.get(LOGOUT_URL), null);
    missingLocalUserUrl = PropertiesUtil.toString(props.get(MISSING_LOCAL_USER_URL),
        DEFAULT_MISSING_LOCAL_USER_URL);
    autoCreateUser = PropertiesUtil.toBoolean(props.get(SSO_AUTOCREATE_USER), DEFAULT_SSO_AUTOCREATE_USER);
    updateAttrsOnLogin = PropertiesUtil.toBoolean(props.get(UPDATE_ATTRS_ON_LOGIN), DEFAULT_UPDATE_ATTRS_ON_LOGIN);
    signatureLocation = PropertiesUtil.toString(props.get(SIGNATURE_LOCATION), DEFAULT_SIGNATURE_LOCATION);
    String certPath = PropertiesUtil.toString(props.get(CERTIFICATE), null);

    loginUrl = serverUrl;
    if (!serverUrl.endsWith("&")) {
      if (serverUrl.contains("?")) {
        loginUrl += "&";
      } else {
        loginUrl += "?";
      }
    }
    loginUrl += entityIdLabel + "=" + entityId;

    matchDestination = PropertiesUtil.toString(props.get(MATCH_DESTINATION), DEFAULT_MATCH_DESTINATION);
    if (MATCH_DESTINATION_NONE.equals(matchDestination)) {
      uriComparator = TRUE_URI_COMPARATOR;
    } else if (MATCH_DESTINATION_SIMPLE.equals(matchDestination)) {
      // only compare the scheme + server + context (no querystring)
      uriComparator = SIMPLE_URI_COMPARATOR;
    } else if (MATCH_DESTINATION_HOST.equals(matchDestination)) {
      uriComparator = HOST_ONLY_URI_COMPARATOR;
    } else {
      uriComparator = new BasicURLComparator();
    }
    decoder.setURIComparator(uriComparator);

    if (certPath != null) {
      File f = new File(certPath);
      if (f.exists() && f.isFile()) {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream is = new BufferedInputStream(new FileInputStream(f));
        certificate = (X509Certificate) cf.generateCertificate(is);
      } else {
        CertificateFactory cf = CertificateFactory.getInstance("X.509-base64");
        InputStream is = new ByteArrayInputStream(certPath.getBytes());
        certificate = (X509Certificate) cf.generateCertificate(is);
      }
    } else {
      certificate = null;
    }
  }

  // ----------- AuthenticationHandler interface ----------------------------
  public void dropCredentials(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.sendRedirect(logoutUrl);
  }

  public AuthenticationInfo extractCredentials(HttpServletRequest request,
      HttpServletResponse response) {
    LOGGER.debug("extractCredentials called");

    // We can only work with POSTs from the IdP
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      LOGGER.debug("not a POST; returning null.");
      return null;
    } else if (request.getParameter("SAMLResponse") == null) {
      LOGGER.debug("nothing to see here; proceed [missing saml response]");
      return null;
    }

    LOGGER.info("Attempting to extract credentials from SAML response.");

    // set the trigger on the request to acknowledge that we accept a call from the SSO
    // server and do not need to ask them for information again even if the information
    // we receive is bad or something goes weird locally. This is used by SamlLoginServlet
    request.setAttribute(SamlLoginServlet.TRY_LOGIN, "2");

    AuthenticationInfo authnInfo = null;

    try {
      // this can return null username which will cause the authn plugin to fail
      // and thusly call the authenticationFailed of this handler
      authnInfo = processSAMLRequest(request);

      boolean isUserValid = isUserValid(authnInfo);
      if (!isUserValid) {
        LOGGER.warn("SSO authentication succeeded but corresponding user not found nor created");

        // if processing found the response invalid, we should consider the authn failed
        if (authnInfo == null) {
          authnInfo = AuthenticationInfo.FAIL_AUTH;
        }
      }
      request.setAttribute(AUTHN_INFO, authnInfo);
    } catch (MessageDecodingException e1) {
      LOGGER.error("Unable to decode incoming SAML Request", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    } catch (KeyStoreException e1) {
      LOGGER.error("An exception occured when accessing keystore", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    } catch (NoSuchAlgorithmException e1) {
      LOGGER.error("Exception where Algorithm not found", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    } catch (CertificateException e1) {
      LOGGER.error("Error in accessing certificate", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    } catch (SecurityException e1) {
      LOGGER.error("Unable to decode incoming SAML Request", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    } catch (UnmarshallingException e1) {
      LOGGER.error("Unable to Unmarshal incoming SAML Assertion", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    } catch (ValidationException e1) {
      LOGGER.error("Unable to validate SAML assertion", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    } catch (IOException e1) {
      LOGGER.error("Error occured accessing keystore", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    } catch (ConfigurationException e1) {
      LOGGER.error("Error occured in openSAML config initialization", e1);
      authnInfo = AuthenticationInfo.FAIL_AUTH;
    }

    return authnInfo;
  }

  /**
   * Called after extractCredentials has returned non-null but logging into the repository
   * with the provided AuthenticationInfo failed. Also called from
   * {@link SamlLoginServlet}.<br/>
   *
   * {@inheritDoc}
   *
   * @see org.apache.sling.auth.core.spi.AuthenticationHandler#requestCredentials(javax.servlet.http.HttpServletRequest,
   *      javax.servlet.http.HttpServletResponse)
   */
  public boolean requestCredentials(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    LOGGER.debug("requestCredentials called");

    String toUrl = null;
    if ("2".equals(request.getAttribute(SamlLoginServlet.TRY_LOGIN))) {
      LOGGER.info("Giving up on logging in. Already tried and failed.");
      toUrl = missingLocalUserUrl;
    } else {
      LOGGER.info("Redirecting to: \"{}\"", loginUrl);
      toUrl = loginUrl;

      // check for a 'url' parameter from the UI. If found, send it to SSO as RelayState
      // so we can redirect there after login which is handled in the servlet
      String relayState = request.getParameter("resource");
      if (!StringUtils.isBlank(relayState)) {
        toUrl += "&RelayState=" + relayState;
      }
    }
    response.sendRedirect(toUrl);
    return true;
  }

  // ----------- AuthenticationFeedbackHandler interface ----------------------------

  /**
   * {@inheritDoc}
   *
   * @see org.apache.sling.auth.core.spi.AuthenticationFeedbackHandler#authenticationFailed(javax.servlet.http.HttpServletRequest,
   *      javax.servlet.http.HttpServletResponse,
   *      org.apache.sling.auth.core.spi.AuthenticationInfo)
   */
  public void authenticationFailed(HttpServletRequest request,
      HttpServletResponse response, AuthenticationInfo authInfo) {
    LOGGER.debug("Failed authentication");
    try {
      response.sendRedirect(missingLocalUserUrl);
    } catch (IOException e) {
      LOGGER.error("Failed to execute SAML authentication failure redirect.", e);
    }
  }

  /**
   * If a redirect is configured, this method will take care of the redirect.
   * <p>
   * If user auto-creation is configured, this method will check for an existing
   * Authorizable that matches the principal. If not found, it creates a new Jackrabbit
   * user with all properties blank except for the ID and a randomly generated password.
   * WARNING: Currently this will not perform the extra work done by the Nakamura
   * CreateUserServlet, and the resulting user will not be associated with a valid
   * profile.
   * <p>
   * Note: do not try to inject the token here. The request has not had the authenticated
   * user added to it so request.getUserPrincipal() and request.getRemoteUser() both
   * return null.
   * <p>
   * TODO This really needs to be dropped to allow for user pull, person directory
   * integrations, etc. See SLING-1563 for the related issue of user population via
   * OpenID.
   *
   * @see org.apache.sling.auth.core.spi.AuthenticationFeedbackHandler#authenticationSucceeded(javax.servlet.http.HttpServletRequest,
   *      javax.servlet.http.HttpServletResponse,
   *      org.apache.sling.auth.core.spi.AuthenticationInfo)
   */
  public boolean authenticationSucceeded(HttpServletRequest request,
      HttpServletResponse response, AuthenticationInfo authInfo) {
    LOGGER.debug("Successful authentication. Sending to default redirect.");

    // Check for the default post-authentication redirect.
    return DefaultAuthenticationFeedbackHandler.handleRedirect(request, response);
  }

  // ----------- Internal ----------------------------
  private boolean isUserValid(AuthenticationInfo authInfo) {
    if (authInfo == null) {
      return false;
    }

    boolean isUserValid = false;
    final String username = authInfo.getUser();
    // Check for a matching Authorizable. If one isn't found, create
    // a new user.
    Session session = null;
    try {
      session = repository.loginAdministrative(); // usage checked and
      // ok KERN-577
      AuthorizableManager authMgr = session.getAuthorizableManager();
      Authorizable authorizable = authMgr.findAuthorizable(username);
      if (authorizable == null) {
        if (autoCreateUser && createUser(authInfo, session) != null) {
          isUserValid = true;
        }
      } else {
        if (updateAttrsOnLogin) {
          // copy attrs to user
          authorizable.setProperty("firstName", authInfo.get("firstname"));
          authorizable.setProperty("lastName", authInfo.get("lastname"));
          authorizable.setProperty("email", authInfo.get("email"));
        }
        isUserValid = true;
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
    }
    return isUserValid;
  }

  /**
   * TODO This logic should probably be supplied by a shared service rather than copied
   * and pasted across components.
   */
  private User createUser(AuthenticationInfo authnInfo, Session session) throws Exception {
    LOGGER.info("Creating user {}", authnInfo.getUser());
    AuthorizableManager authMgr = session.getAuthorizableManager();
    if (authMgr.createUser(authnInfo.getUser(), authnInfo.getUser(),
        RandomStringUtils.random(32), null)) {
      LOGGER.info("User {} created", authnInfo.getUser());
      User user = (User) authMgr.findAuthorizable(authnInfo.getUser());
      Map<String, Object[]> props = new HashMap<String, Object[]>();
      props.put("firstName", new Object[] { authnInfo.get("firstname") });
      props.put("lastName", new Object[] { authnInfo.get("lastname") });
      props.put("email", new Object[] { authnInfo.get("email") });

      if (authzPostProcessService != null) {
        authzPostProcessService.process(user, session, ModificationType.CREATE, props);
      }

      if (user.isModified()) {
        authMgr.updateAuthorizable(user);
      }
      return user;
    }
    return null;
  }

  /**
   * Processes the request to extract the SAML response and any information contained
   * therein.
   *
   * @param request
   * @return null if the SAML response was found to be out of date or mismatches the
   *         intended audience. Will have a null username if there is a problem processing
   *         the subject of the response. Will contain any found attributes and the
   *         subject otherwise.
   * @throws MessageDecodingException
   * @throws SecurityException
   * @throws UnmarshallingException
   * @throws ValidationException
   * @throws KeyStoreException
   * @throws NoSuchAlgorithmException
   * @throws CertificateException
   * @throws IOException
   * @throws ConfigurationException
   */
  private AuthenticationInfo processSAMLRequest(HttpServletRequest request)
      throws MessageDecodingException, SecurityException, UnmarshallingException,
      ValidationException, KeyStoreException, NoSuchAlgorithmException,
      CertificateException, IOException, ConfigurationException {

    HttpServletRequestAdapter adapter = new HttpServletRequestAdapter(request);
    SAMLMessageContext<SignableSAMLObject, SignableSAMLObject, SAMLObject> context = new BasicSAMLMessageContext<SignableSAMLObject, SignableSAMLObject, SAMLObject>();
    context.setInboundMessageTransport(adapter);
    decoder.decode(context);
    SignableSAMLObject message = context.getInboundSAMLMessage();

    if (SIGNATURE_MESSAGE.equals(signatureLocation)) {
      if (certificate != null && message.isSigned()) {
        validateSignature(message);
      } else {
        checkCertificate(message);
      }
    }

    Element samlElement = message.getDOM();
    UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
    Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(samlElement);

    Response response = (Response) unmarshaller.unmarshall(samlElement);
    List<Assertion> assertions = response.getAssertions();
    AuthenticationInfo authnInfo = null;
    if (assertions != null && assertions.size() > 0) {
      Assertion assertion = assertions.get(0);

      if (SIGNATURE_ASSERTION.equals(signatureLocation)) {
        if (certificate != null && assertion.isSigned()) {
          validateSignature(assertion);
        } else {
          checkCertificate(assertion);
        }
      }

      Subject subject = assertion.getSubject();
      Conditions conditions = assertion.getConditions();
      String url = request.getRequestURL().toString();
      Date date = new Date();

      // validate the date range of the subject confirmation
      boolean validConfirmation = validConfirmation(subject, url, date);

      // validate the date range of the conditions
      boolean validConditions = validConditions(conditions, url, date);

      // assertion has the proper date range and we're the intended audience
      LOGGER.info("Assertion validation: confirmation: {}, conditions: {}",
          validConfirmation, validConditions);
      if (validConfirmation && validConditions) {
        NameID nameId = subject.getNameID();
        String netId = nameId.getValue();

        authnInfo = new AuthenticationInfo(AUTH_TYPE, netId);
        final SamlPrincipal principal = new SamlPrincipal(netId);
        SimpleCredentials credentials = new SimpleCredentials(principal.getName(),
            new char[] {});
        credentials.setAttribute(SamlPrincipal.class.getName(), principal);
        authnInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_CREDENTIALS, credentials);

        for (AttributeStatement attrStmt : assertion.getAttributeStatements()) {
          for (Attribute attr : attrStmt.getAttributes()) {
            List<Object> vals = Lists.newArrayList();
            List<XMLObject> values = attr.getAttributeValues();
            for (XMLObject xml : values) {
              vals.add(xml.getDOM().getTextContent());
            }
            if (vals.size() == 1) {
              authnInfo.put(attr.getName(), vals.get(0));
            } else if (vals.size() > 1) {
              authnInfo.put(attr.getName(), vals);
            }
          }
        }
      }
    }
    return authnInfo;
  }

  /**
   * Validate that the signature found matches our certificate.
   *
   * @param signature
   * @throws ValidationException
   */
  private void validateSignature(SignableSAMLObject samlObj) throws ValidationException {
    Signature signature = samlObj.getSignature();
    profileValidator.validate(signature);
    BasicX509Credential credential = new BasicX509Credential();
    credential.setEntityCertificate(certificate);
    SignatureValidator sigValidator = new SignatureValidator(credential);
    sigValidator.validate(signature);
  }

  private void checkCertificate(SignableSAMLObject samlObj) throws ValidationException {
    boolean isSigned = samlObj.isSigned();
    String msg = null;
    if (certificate == null && isSigned) {
      msg = "Unable to validate signature; no certificate configured.";
    } else if (certificate != null && !isSigned) {
      msg = "Unable to validate signature; no signature found on object.";
    }
    if (msg != null) {
      LOGGER.error(msg);
      throw new ValidationException(msg);
    }
  }

  private boolean validConditions(Conditions conditions, String url, Date date) {
    if (conditions == null) {
      return true;
    }

    boolean validConditions = false;
    DateTime notBefore = conditions.getNotBefore();
    DateTime notOnOrAfter = conditions.getNotOnOrAfter();

    if (date.after(notBefore.toDate()) && date.before(notOnOrAfter.toDate())) {
      List<AudienceRestriction> restrictions = conditions.getAudienceRestrictions();
      if (restrictions != null && restrictions.size() > 0) {
        for (AudienceRestriction restriction : restrictions) {
          List<Audience> audiences = restriction.getAudiences();
          for (Audience audience : audiences) {
            if (uriComparator.compare(url, audience.getAudienceURI())) {
              validConditions = true;
              break;
            }
          }
        }
      } else {
        validConditions = true;
      }
    }
    return validConditions;
  }

  private boolean validConfirmation(Subject subject, String url, Date date) {
    boolean validConfirmation = false;
    List<SubjectConfirmation> confirmations = subject.getSubjectConfirmations();
    if (confirmations != null && confirmations.size() > 0) {
      SubjectConfirmationData data = confirmations.get(0).getSubjectConfirmationData();
      DateTime notOnOrAfter = data.getNotOnOrAfter();

      if (date.before(notOnOrAfter.toDate()) && uriComparator.compare(url, data.getRecipient())) {
        validConfirmation = true;
      }
    } else {
      validConfirmation = true;
    }
    return validConfirmation;
  }

  static final class SamlPrincipal implements Principal {
    private String principalName;

    public SamlPrincipal(String principalName) {
      this.principalName = principalName;
    }

    /**
     * {@inheritDoc}
     *
     * @see java.security.Principal#getName()
     */
    public String getName() {
      return principalName;
    }
  }
}
