package io.mopl.global.util;

public class InitialUtils {

  private static final char[] Initial_LIST = {
      'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
      'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
  };

  public static String extractInitial(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    StringBuilder result = new StringBuilder();
    for (char c : text.toCharArray()) {
      if (c >= 0xAC00 && c <= 0xD7A3) {
        int choIdx = (c - 0xAC00) / 28 / 21;
        result.append(Initial_LIST[choIdx]);
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}