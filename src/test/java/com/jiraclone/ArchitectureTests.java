package com.jiraclone;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** AUTH-9: only identity and the shared security configuration touch Spring Security user types. */
class ArchitectureTests {

	private final JavaClasses classes = new ClassFileImporter()
			.withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("com.jiraclone");

	@Test
	void securityUserTypesStayInIdentityAndCommon() {
		noClasses()
				.that().resideOutsideOfPackages("com.jiraclone.identity..", "com.jiraclone.common..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"org.springframework.security.core.userdetails..",
						"org.springframework.security.core.context..",
						"org.springframework.security.core")
				.check(classes);
	}

	@Test
	void identityInternalsAreNotUsedElsewhere() {
		noClasses()
				.that().resideOutsideOfPackage("com.jiraclone.identity..")
				.should().dependOnClassesThat().resideInAnyPackage("com.jiraclone.identity.core..", "com.jiraclone.identity.web..")
				.check(classes);
	}
}
