package io.mopl.global.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtProvider {

  private final MACSigner signer;

  private final MACVerifier verifier;

  private final long accessTokenValiditySeconds;
  private final long refreshTokenValiditySeconds;

  public JwtProvider(
      @Value("${jwt.secret}")
      String secret,

      @Value("${jwt.access-token-validity-seconds}")
      long accessTokenValiditySeconds,

      @Value("${jwt.refresh-token-validity-seconds}")
      long refreshTokenValiditySeconds
  ) throws JOSEException {

    byte[] secretKey = Base64.getDecoder().decode(secret);

    this.signer = new MACSigner(secretKey);

    this.verifier = new MACVerifier(secretKey);

    this.accessTokenValiditySeconds = accessTokenValiditySeconds;

    this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;

    log.info("JwtTokenProvider initialized");
  }

  public String generateAccessToken(UserDetails userDetails) {
    try {
      Instant now = Instant.now();
      Instant expiration = now.plusSeconds(accessTokenValiditySeconds);

      List<String> authorities = userDetails.getAuthorities()
          .stream()
          .map(GrantedAuthority::getAuthority)
          .toList();

      MoplUserDetails moplUser = (MoplUserDetails) userDetails;
      String userIdStr = moplUser.getUser().getId().toString();

      JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
          .subject(userDetails.getUsername())
          .issueTime(Date.from(now))
          .expirationTime(Date.from(expiration))
          .jwtID(UUID.randomUUID().toString())
          .claim("authorities", authorities)
          .claim("userId", userIdStr)
          .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
      signedJWT.sign(signer);

      return signedJWT.serialize();

    } catch (Exception e) {
      log.error("JwtProvider Access Token Create Fail", e);
      throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  public boolean validateToken(String token) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(token);

      if (!signedJWT.verify(verifier)) {
        return false;
      }

      Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();

      return expirationTime.after(new Date());

    } catch (Exception e) {
      return false;
    }
  }

  public String getUsername(String token) {

    try {
      SignedJWT signedJWT = SignedJWT.parse(token);

      return signedJWT
          .getJWTClaimsSet()
          .getSubject();

    } catch (ParseException e) {
      log.error("JwtProvider Token Parsing Fail", e);
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
  }

  public UUID getUserId(String token) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(token);

      String userIdStr = signedJWT.getJWTClaimsSet().getStringClaim("userId");

      return UUID.fromString(userIdStr);

    } catch (Exception e) {
      log.error("Failed to extract userId from token", e);
      throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  public String generateRefreshToken(String email) {
    try {
      Instant now = Instant.now();
      Instant expiration = now.plusSeconds(refreshTokenValiditySeconds);

      JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
          .subject(email)
          .issueTime(Date.from(now))
          .expirationTime(Date.from(expiration))
          .jwtID(UUID.randomUUID().toString())
          .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
      signedJWT.sign(signer);
      return signedJWT.serialize();

    } catch (Exception e) {
      log.error("JwtProvider Refresh Token Create Fail", e);
      throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
