/*
 * Copyright © 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.exhort.providers;

import com.redhat.exhort.Api;
import com.redhat.exhort.ExhortTest;
import com.redhat.exhort.tools.Operations;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(HelperExtension.class)
@ExtendWith(MockitoExtension.class)
class Gradle_Provider_Test extends ExhortTest {
//  private static System.Logger log = System.getLogger("Gradle_Provider_Test");
  // test folder are located at src/test/resources/tst_manifests
  // each folder should contain:
  // - build.gradle: the target manifest for testing
  // - expected_sbom.json: the SBOM expected to be provided
  static Stream<String> testFolders() {
    return Stream.of(
//      "deps_with_no_ignore_provided_scope",
//      "deps_no_trivial_with_ignore",
//      "deps_with_ignore_on_artifact",
//      "deps_with_ignore_on_dependency",
//      "deps_with_ignore_on_group",
//      "deps_with_ignore_on_version",
//      "deps_with_ignore_on_wrong",
//      "deps_with_no_ignore",
      "deps_with_no_ignore_common_paths"
    );
  }

  @ParameterizedTest
  @MethodSource("testFolders")
  void test_the_provideStack(String testFolder) throws IOException, InterruptedException {
    // create temp file hosting our sut build.gradle
    var tmpGradleDir = Files.createTempDirectory("exhort_test_");
    var tmpGradleFile = Files.createFile(tmpGradleDir.resolve("build.gradle"));
//    log.log(System.Logger.Level.INFO,"the test folder is : " + testFolder);
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "build.gradle"))) {
      Files.write(tmpGradleFile, is.readAllBytes());
    }

    // load expected SBOM
    String expectedSbom;
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "expected_stack_sbom.json"))) {
      expectedSbom = new String(is.readAllBytes());
    }
    String depTree;
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "depTree.txt"))) {
      depTree = new String(is.readAllBytes());
    }

    MockedStatic<Operations> mockedOperations = mockStatic(Operations.class, Mockito.CALLS_REAL_METHODS);
    ArgumentMatcher<Path> matchPath = path -> path == null;
    mockedOperations.when(() -> Operations.runProcessGetOutput(argThat(matchPath),any(String[].class))).thenReturn(expectedSbom);

    // when providing stack content for our pom
    var content = new GradleProvider().provideStack(tmpGradleFile);
    // cleanup
    Files.deleteIfExists(tmpGradleFile);
    // verify expected SBOM is returned
    mockedOperations.close();
    assertThat(content.type).isEqualTo(Api.CYCLONEDX_MEDIA_TYPE);
    assertThat(dropIgnored(new String(content.buffer)))
      .isEqualTo(dropIgnored(expectedSbom));

  }

  private static String getOutputFileAndOverwriteItWithMock(String outputFileContent, InvocationOnMock invocationOnMock,String parameterPrefix) throws IOException {
    String[] rawArguments = (String[]) invocationOnMock.getRawArguments()[0];
    Optional<String> outputFileArg = Arrays.stream(rawArguments).filter(arg -> arg!= null && arg.startsWith(parameterPrefix)).findFirst();
    String outputFilePath=null;
    if(outputFileArg.isPresent())
    {
      String outputFile = outputFileArg.get();
      outputFilePath = outputFile.substring(outputFile.indexOf("=") + 1);
      Files.writeString(Path.of(outputFilePath), outputFileContent);
    }
    return outputFilePath;
  }

  @ParameterizedTest
  @MethodSource("testFolders")
  void test_the_provideComponent(String testFolder) throws IOException, InterruptedException {
    // load the pom target pom file
    byte[] targetGradleBuild;
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "build.gradle"))) {
      targetGradleBuild = is.readAllBytes();
    }
    // load expected SBOM
    String expectedSbom = "";
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "expected_component_sbom.json"))) {
      expectedSbom = new String(is.readAllBytes());
    }

    String effectivePom;
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "effectivePom.xml"))) {
      effectivePom = new String(is.readAllBytes());
    }

    MockedStatic<Operations> mockedOperations = mockStatic(Operations.class);
    mockedOperations.when(() -> Operations.runProcess(any(),any())).thenAnswer(invocationOnMock -> {
      return getOutputFileAndOverwriteItWithMock(effectivePom, invocationOnMock,"-Doutput");
    });

    // when providing component content for our pom
    var content = new GradleProvider().provideComponent(targetGradleBuild);
    mockedOperations.close();
    // verify expected SBOM is returned
    assertThat(content.type).isEqualTo(Api.CYCLONEDX_MEDIA_TYPE);
    assertThat(dropIgnored(new String(content.buffer)))
      .isEqualTo(dropIgnored(expectedSbom));

  }
  @ParameterizedTest
  @MethodSource("testFolders")
  void test_the_provideComponent_With_Path(String testFolder) throws IOException, InterruptedException {
    // load the pom target pom file
    // create temp file hosting our sut build.gradle
    var tmpPomFile = Files.createTempFile("exhort_test_", ".xml");
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "build.gradle"))) {
      Files.write(tmpPomFile, is.readAllBytes());
    }
    // load expected SBOM
    String expectedSbom = "";
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "expected_component_sbom.json"))) {
      expectedSbom = new String(is.readAllBytes());
    }

    String effectivePom;
    try (var is = getClass().getClassLoader().getResourceAsStream(String.join("/","tst_manifests", "gradle", testFolder, "effectivePom.xml"))) {
      effectivePom = new String(is.readAllBytes());
    }

    MockedStatic<Operations> mockedOperations = mockStatic(Operations.class);
    mockedOperations.when(() -> Operations.runProcess(any(),any())).thenAnswer(invocationOnMock -> {
      return getOutputFileAndOverwriteItWithMock(effectivePom, invocationOnMock,"-Doutput");
    });

    // when providing component content for our pom
    var content = new GradleProvider().provideComponent(tmpPomFile);
    // verify expected SBOM is returned
    mockedOperations.close();
    assertThat(content.type).isEqualTo(Api.CYCLONEDX_MEDIA_TYPE);
    assertThat(dropIgnored(new String(content.buffer)))
      .isEqualTo(dropIgnored(expectedSbom));

  }

  private String dropIgnored(String s) {
    return s.replaceAll("\\s+","").replaceAll("\"timestamp\":\"[a-zA-Z0-9\\-\\:]+\",", "");
  }
}
