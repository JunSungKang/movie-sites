package xyz.gg_jskang.moviesites.architecture;

import static org.junit.jupiter.api.Assertions.*;
import com.tngtech.archunit.core.domain.*;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Inspects compiled dependencies, including method calls, fields, generics and exception types. */
class ArchitectureTests {
    private static final String ROOT="xyz.gg_jskang.moviesites";
    private static final JavaClasses PRODUCTION=new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);

    static List<String> boundaryViolations(JavaClasses classes,String root) {
        List<String> violations=new ArrayList<>();
        for(JavaClass type:classes)for(Dependency dependency:type.getDirectDependenciesFromSelf()) {
            String origin=dependency.getOriginClass().getPackageName(),target=dependency.getTargetClass().getPackageName();
            String from=feature(origin,root),to=feature(target,root);
            if(from==null||to==null||from.equals(to))continue;
            if(!target.equals(root+"."+to))violations.add(dependency.getDescription());
        }
        return violations;
    }
    static Map<String,Set<String>> featureGraph(JavaClasses classes,String root) {
        Map<String,Set<String>> graph=new TreeMap<>();
        for(JavaClass type:classes)for(Dependency dependency:type.getDirectDependenciesFromSelf()) {
            String from=feature(dependency.getOriginClass().getPackageName(),root),to=feature(dependency.getTargetClass().getPackageName(),root);
            if(from!=null&&to!=null&&!from.equals(to))graph.computeIfAbsent(from,key->new TreeSet<>()).add(to);
        }
        return graph;
    }
    private static String feature(String pkg,String root) {
        if(!pkg.startsWith(root+"."))return null;
        return pkg.substring(root.length()+1).split("\\.")[0];
    }
    static boolean cyclic(Map<String,Set<String>> graph) {
        Set<String> visited=new HashSet<>(),active=new HashSet<>();
        for(String feature:graph.keySet())if(visit(feature,graph,visited,active))return true;
        return false;
    }
    private static boolean visit(String node,Map<String,Set<String>> graph,Set<String> visited,Set<String> active) {
        if(active.contains(node))return true;if(!visited.add(node))return false;
        active.add(node);for(String next:graph.getOrDefault(node,Set.of()))if(visit(next,graph,visited,active))return true;
        active.remove(node);return false;
    }
    @Test void featuresUseOnlyOtherFeaturesPublicEntrypoints() {
        assertTrue(PRODUCTION.size()>30,"Production classes were not imported");
        assertEquals(List.of(),boundaryViolations(PRODUCTION,ROOT));
    }
    @Test void featureDependenciesAreAcyclic() {
        var graph=featureGraph(PRODUCTION,ROOT);assertFalse(cyclic(graph),graph.toString());
    }
    @Test void publicApiSignaturesDoNotExposeImplementationTypes() {
        List<String> leaks=new ArrayList<>();
        for(JavaClass type:PRODUCTION) {
            String feature=feature(type.getPackageName(),ROOT);
            if(feature==null||!type.getPackageName().equals(ROOT+"."+feature))continue;
            for(JavaMethod method:type.getMethods())if(method.getModifiers().contains(JavaModifier.PUBLIC)) {
                List<JavaClass> signature=new ArrayList<>(method.getRawParameterTypes());signature.add(method.getRawReturnType());
                for(JavaClass referenced:signature)if(referenced.getPackageName().startsWith(ROOT)&&referenced.getPackageName().contains(".internal"))leaks.add(method.getFullName());
            }
            for(JavaConstructor constructor:type.getConstructors())if(constructor.getModifiers().contains(JavaModifier.PUBLIC))
                for(JavaClass referenced:constructor.getRawParameterTypes())if(referenced.getPackageName().startsWith(ROOT)&&referenced.getPackageName().contains(".internal"))leaks.add(constructor.getFullName());
        }
        assertEquals(List.of(),leaks);
    }
    @Test void controllersStayInsideFeatureWebPackages() {
        for(JavaClass type:PRODUCTION)if(type.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)||type.isAnnotatedWith(org.springframework.web.bind.annotation.RestControllerAdvice.class))assertTrue(type.getPackageName().endsWith(".web"),type.getName());
    }
    @Test void realForbiddenBytecodeExamplesAreRejected() {
        String fixtures=ROOT+".architecture.fixtures";
        JavaClasses samples=new ClassFileImporter().importPackages(fixtures);
        var failures=boundaryViolations(samples,fixtures);
        assertTrue(failures.stream().anyMatch(text->text.contains("Hidden")),failures.toString());
        assertTrue(failures.stream().anyMatch(text->text.contains("Endpoint")),failures.toString());
        assertTrue(cyclic(featureGraph(samples,fixtures)),"Real cross-feature field references must form a detected cycle");
    }
}
