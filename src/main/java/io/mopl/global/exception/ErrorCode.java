package io.mopl.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

  // 공통
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류"),
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다"),
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON_409", "이미 존재하는 데이터입니다"),
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_429", "요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요."),

  // User
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다"),
  EMAIL_DUPLICATION(HttpStatus.CONFLICT, "USER_409", "이메일이 이미 존재합니다"),
  LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "USER_401", "이메일 또는 비밀번호가 틀렸습니다"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "USER_403", "권한이 없습니다"),

  // Auth
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_401", "인증 정보가 필요합니다"),

  // Direct Message
  SELF_CONVERSATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DM_400", "자기 자신과는 대화를 생성할 수 없습니다"),
  CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "DM_404", "대화를 찾을 수 없습니다"),
  // auth
  INVALID_EMAIL(HttpStatus.NOT_FOUND, "AUTH_404", "이메일이 일치하지 않습니다."),
  INVALID_PASSWORD(HttpStatus.NOT_FOUND, "AUTH_403", "비밀번호가 일치하지 않습니다."),

  // 참여자 파악 여부
  NOT_CHAT_PARTICIPANT(HttpStatus.FORBIDDEN, "CHAT_403","채팅방 참여자가 아닙니다."),

  // WatchingSession
  WATCHING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "WATCHING_SESSION_404", "현재 시청 중인 콘텐츠가 없습니다"),
  WATCHING_SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "WATCHING_SESSION_409", "이미 다른 콘텐츠를 시청 중입니다"),
  INVALID_WATCHING_SESSION_CURSOR(HttpStatus.BAD_REQUEST, "WATCHING_SESSION_CURSOR_400", "시청 목록을 불러올 수 없습니다"),
  INVALID_WATCHING_SESSION_SORT(HttpStatus.BAD_REQUEST, "WATCHING_SESSION_SORT_400", "시청 목록을 정렬할 수 없습니다"),

  // 플레이리스트
  PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAYLIST_404", "플레이리스트를 찾을 수 없습니다"),
  PLAYLIST_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAYLIST_404", "플레이리스트에 해당 콘텐츠가 없습니다"),

  // Direct Message
  SELF_CONVERSATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DM_400", "자기 자신과는 대화를 생성할 수 없습니다"),
  CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "DM_404", "대화를 찾을 수 없습니다"),
  CONVERSATION_CREATE_RACE_CONDITION(HttpStatus.CONFLICT, "DM_409", "대화 생성 중 충돌이 발생했습니다"),

  // 리뷰
  ALREADY_REVIEWED(HttpStatus.CONFLICT, "REVIEW_409", "이미 작성한 리뷰가 존재합니다.")
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}