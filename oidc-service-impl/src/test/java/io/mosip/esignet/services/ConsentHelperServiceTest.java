/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.KafkaHelperService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@RunWith(MockitoJUnitRunner.class)
public class ConsentHelperServiceTest {


    @Mock
    ConsentService consentService;

    @Mock
    KafkaHelperService kafkaHelperService;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    ConsentHelperService consentHelperService;

    @Autowired
    ObjectMapper objectMapper;

    @Mock
    AuditPlugin auditHelper;


    @Test
    public void addUserConsent_withValidLinkedTransaction_thenPass()
    {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of("name"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoNameClaimDetail = new ClaimDetail("name", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("token", new String[]{"value2a", "value2b"}, false);
        userinfo.put("name", userinfoNameClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);
        consentHelperService.updateUserConsent(oidcTransaction, true, null);
        UserConsent userConsent = new UserConsent();
        userConsent.setHash("PxQIckCdFC5TPmL7_G7NH0Zs4UmHC74rGpOkyldqRpg");
        userConsent.setClaims(claims);
        userConsent.setAuthorizationScopes(Map.of());
        userConsent.setAcceptedClaims(List.of("name"));
        Mockito.verify(consentService).saveUserConsent(userConsent);

    }

    @Test
    public void addUserConsent_withValidWebTransaction_thenPass()
    {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of("value1"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoClaimDetail = new ClaimDetail("value1", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("value2", new String[]{"value2a", "value2b"}, false);
        userinfo.put("userinfoKey", userinfoClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        Mockito.when(consentService.saveUserConsent(Mockito.any())).thenReturn(new ConsentDetail());

        consentHelperService.updateUserConsent(oidcTransaction, false, "");
        UserConsent userConsent = new UserConsent();
        userConsent.setHash("Cgh8oWpNM84WPYQVvluGj616_kd4z60elVXtc7R_lXw");
        userConsent.setClaims(claims);
        userConsent.setAuthorizationScopes(Map.of());
        userConsent.setAcceptedClaims(List.of("value1"));
        userConsent.setSignature("");
        Mockito.verify(consentService).saveUserConsent(userConsent);
    }

    @Test
    public void addUserConsent_withValidWebTransactionNoClaimsAndScopes_thenPass()
    {
        String clientId  = "clientId";
        String psuToken = "psuToken";
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        oidcTransaction.setConsentAction(ConsentAction.NOCAPTURE);
        oidcTransaction.setVoluntaryClaims(List.of());
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setAcceptedClaims(List.of());
        oidcTransaction.setPermittedScopes(List.of());
        oidcTransaction.setClientId(clientId);
        oidcTransaction.setPartnerSpecificUserToken(psuToken);
        consentHelperService.updateUserConsent(oidcTransaction, false, "");
        Mockito.verify(consentService).deleteUserConsent(clientId, psuToken);
    }

    @Test
    public void processConsent_withValidConsentAndConsentActionAsNoCapture_thenPass() throws JsonProcessingException {

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));
        oidcTransaction.setEssentialClaims(List.of("name"));
        oidcTransaction.setVoluntaryClaims(List.of("email"));

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoNameClaimDetail = new ClaimDetail("name", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("token", new String[]{"value2a", "value2b"}, false);
        userinfo.put("name", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",false,"profile",false));
        consentDetail.setClaims(claims);
        Claims normalizedClaims = new Claims();
        normalizedClaims.setUserinfo(consentHelperService.normalizeClaims(claims.getUserinfo()));
        normalizedClaims.setId_token(consentHelperService.normalizeClaims(claims.getId_token()));
        String hashCode =consentHelperService.hashUserConsent(normalizedClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));

        consentHelperService.processConsent(oidcTransaction,true);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.NOCAPTURE);
        Assert.assertEquals(oidcTransaction.getAcceptedClaims(),consentDetail.getAcceptedClaims());
        Assert.assertEquals(oidcTransaction.getPermittedScopes(),consentDetail.getPermittedScopes());
    }

    @Test
    public void processConsent_withValidConsentAndConsentActionAsCapture_thenPass() throws JsonProcessingException {

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));
        oidcTransaction.setPermittedScopes(List.of("openid","profile"));
        oidcTransaction.setEssentialClaims(List.of("name"));
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoNameClaimDetail = new ClaimDetail("name", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("token", new String[]{"value2a", "value2b"}, false);
        userinfo.put("name", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setClientId("123");
        consentDetail.setSignature("signature");
        consentDetail.setAuthorizationScopes(Map.of("openid",true,"profile",true));

        Claims consentClaims = new Claims();
        userinfo = new HashMap<>();
        id_token = new HashMap<>();
        userinfoNameClaimDetail = new ClaimDetail("gender", new String[]{"value1a", "value1b"}, false);
        idTokenClaimDetail = new ClaimDetail("token", new String[]{"value1a", "value2b"}, false);
        userinfo.put("gender", userinfoNameClaimDetail);
        userinfo.put("email",null);
        id_token.put("idTokenKey", idTokenClaimDetail);
        consentClaims.setUserinfo(userinfo);
        consentClaims.setId_token(id_token);

        consentDetail.setClaims(consentClaims);
        String hashCode =consentHelperService.hashUserConsent(consentClaims,consentDetail.getAuthorizationScopes());
        consentDetail.setHash(hashCode);

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consentDetail));
        consentHelperService.processConsent(oidcTransaction,true);

        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);

    }

    @Test
    public void processConsent_withEmptyConsent_thenPass(){

        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setVoluntaryClaims(List.of("email"));
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.empty());

        consentHelperService.processConsent(oidcTransaction,true);
        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.CAPTURE);

    }

    @Test
    public void processConsent_withEmptyRequestedClaims_thenPass(){
        OIDCTransaction oidcTransaction=new OIDCTransaction();
        oidcTransaction.setClientId("abc");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setVoluntaryClaims(List.of());
        oidcTransaction.setEssentialClaims(List.of());
        oidcTransaction.setRequestedAuthorizeScopes(List.of());
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsuToken(oidcTransaction.getPartnerSpecificUserToken());
        consentHelperService.processConsent(oidcTransaction,true);
        Assert.assertEquals(oidcTransaction.getConsentAction(),ConsentAction.NOCAPTURE);
    }
}