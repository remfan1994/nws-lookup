package vip.thatiam.nwslookup;

import com.sun.source.util.JavacTask;

import java.io.File;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class JavaSyntaxCheck {
    private JavaSyntaxCheck() {
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new AssertionError("system Java compiler is unavailable");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(
                    Arrays.stream(args).map(File::new).toList());
            JavacTask task = (JavacTask) compiler.getTask(
                    null, files, diagnostics, Arrays.asList("-proc:none"), null, units);
            task.parse();
        }

        StringBuilder errors = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.append(diagnostic).append('\n');
            }
        }
        if (errors.length() > 0) throw new AssertionError(errors.toString());
        System.out.println("NWS Lookup Java syntax parse: PASS (" + args.length + " files)");
    }
}
