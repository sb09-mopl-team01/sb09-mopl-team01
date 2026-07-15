package io.mopl.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class LogbackConfigurationTest {

  @Test
  void logbackConfiguration_usesConsoleOnlyForTestProfile() throws Exception {
    Document document = loadLogbackConfiguration();

    Element testProfile = profile(document, "test");
    Element nonTestProfile = profile(document, "!test");

    assertThat(appenderRefs(testProfile)).containsExactly("CONSOLE");
    assertThat(appenderRefs(nonTestProfile)).containsExactly("CONSOLE", "FILE");
  }

  @Test
  void applicationConfiguration_definesConfigurableRollingPolicyDefaults() throws Exception {
    PropertySourcesPropertyResolver resolver = applicationPropertyResolver();

    assertThat(resolver.getProperty("logging.file.name")).isEqualTo("logs/mopl.log");
    assertThat(resolver.getProperty("logging.logback.rollingpolicy.file-name-pattern"))
        .isEqualTo("logs/archive/mopl.%d{yyyy-MM-dd}.%i.log.gz");
    assertThat(resolver.getProperty("logging.logback.rollingpolicy.max-file-size"))
        .isEqualTo("50MB");
    assertThat(resolver.getProperty("logging.logback.rollingpolicy.max-history"))
        .isEqualTo("14");
    assertThat(resolver.getProperty("logging.logback.rollingpolicy.total-size-cap"))
        .isEqualTo("2GB");
    assertThat(resolver.getProperty("logging.logback.rollingpolicy.clean-history-on-start"))
        .isEqualTo("false");
    assertThat(resolver.getProperty("spring.batch.job.enabled")).isEqualTo("false");
    assertThat(resolver.getProperty("mopl.logging.backup.enabled")).isEqualTo("false");
    assertThat(resolver.getProperty("mopl.logging.backup.cron")).isEqualTo("0 0 4 * * *");
    assertThat(resolver.getProperty("mopl.logging.backup.archive-directory"))
        .isEqualTo("logs/archive");
    assertThat(resolver.getProperty("mopl.logging.backup.s3-key-prefix"))
        .isEqualTo("logs/mopl");
  }

  private Document loadLogbackConfiguration() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    try (InputStream input = new ClassPathResource("logback-spring.xml").getInputStream()) {
      return factory.newDocumentBuilder().parse(input);
    }
  }

  private Element profile(Document document, String name) {
    NodeList profiles = document.getElementsByTagName("springProfile");
    for (int index = 0; index < profiles.getLength(); index++) {
      Element profile = (Element) profiles.item(index);
      if (name.equals(profile.getAttribute("name"))) {
        return profile;
      }
    }
    throw new AssertionError("Logback profile not found: " + name);
  }

  private List<String> appenderRefs(Element profile) {
    NodeList refs = profile.getElementsByTagName("appender-ref");
    return IntStream.range(0, refs.getLength())
        .mapToObj(index -> ((Element) refs.item(index)).getAttribute("ref"))
        .toList();
  }

  private PropertySourcesPropertyResolver applicationPropertyResolver() throws Exception {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> loaded = loader.load(
        "application",
        new ClassPathResource("application.yml")
    );
    MutablePropertySources sources = new MutablePropertySources();
    loaded.forEach(sources::addLast);
    return new PropertySourcesPropertyResolver(sources);
  }
}
