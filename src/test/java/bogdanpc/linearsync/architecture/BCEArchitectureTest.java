package bogdanpc.linearsync.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Pragmatic ArchUnit tests to validate BCE (Boundary-Control-Entity) architecture patterns.
 * <p>
 * Focus on the key architectural violations that matter most:
 * - Boundary: Incoming interfaces (CLI, REST endpoints), facade classes for external systems
 * - Control: Business logic and outgoing integrations (HTTP clients)
 * - Entity: Domain models - should be self-contained
 * <p>
 * In multi-component architectures, Control of one component may access Boundary of another
 * component (cross-component communication). This is a valid pattern.
 */
class BCEArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("bogdanpc.linearsync");

    @Test
    void bce_layers_are_respected() {
        // BCE Package Boundaries Rule:
        // - Boundary can talk to control and entity
        // - Control can talk to boundary (cross-component), control, and entity
        // - Entity has no outgoing deps to boundary or control
        var rule = layeredArchitecture().consideringOnlyDependenciesInAnyPackage("bogdanpc.linearsync..").layer("Boundary").definedBy("..boundary..").layer("Control").definedBy("..control..").layer("Entity").definedBy("..entity..")

                // BCE layer access rules (relaxed for multi-component architecture):
                .whereLayer("Boundary").mayOnlyAccessLayers("Control", "Entity").whereLayer("Control").mayOnlyAccessLayers("Boundary", "Control", "Entity").whereLayer("Entity").mayNotAccessAnyLayer();

        rule.check(classes);
    }

    @Test
    void entities_should_not_depend_on_control_or_boundary() {
        var rule = noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat().resideInAPackage("..control..")
            .orShould().dependOnClassesThat().resideInAPackage("..boundary..")
            .allowEmptyShould(true)
            .because("Entity layer should be self-contained and not depend on control or boundary layers");

        rule.check(classes);
    }

    @Test
    void no_cycles_between_business_components() {
        // No Cycles Rule:
        // - No cycles across slices (e.g., orders vs future components)
        slices().matching("bogdanpc.linearsync.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}