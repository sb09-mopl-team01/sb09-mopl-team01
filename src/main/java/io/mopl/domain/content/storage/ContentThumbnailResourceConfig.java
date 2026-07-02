package io.mopl.domain.content.storage;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ContentThumbnailResourceConfig implements WebMvcConfigurer {

  private final Path storagePath;
  private final String urlPrefix;

  public ContentThumbnailResourceConfig(
      @Value("${mopl.content.thumbnail.storage-path:build/content-thumbnails}") String storagePath,
      @Value("${mopl.content.thumbnail.url-prefix:/content-thumbnails}") String urlPrefix
  ) {
    this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
    this.urlPrefix = normalizeUrlPrefix(urlPrefix);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler(urlPrefix + "/**")
        .addResourceLocations(storagePath.toUri().toString());
  }

  private String normalizeUrlPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      return "/content-thumbnails";
    }
    String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
    return normalized.endsWith("/")
        ? normalized.substring(0, normalized.length() - 1)
        : normalized;
  }
}
