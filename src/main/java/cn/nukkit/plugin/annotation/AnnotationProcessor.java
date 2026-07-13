package cn.nukkit.plugin.annotation;

import cn.nukkit.plugin.PluginLoadOrder;

import javax.annotation.processing.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Set;

@SupportedAnnotationTypes({"cn.nukkit.plugin.annotation.PluginMeta"})
public class AnnotationProcessor extends AbstractProcessor {
    private Elements elements;
    private Types types;
    private Messager messager;
    private Filer filer;

    private boolean generated = false;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.elements = env.getElementUtils();
        this.types = env.getTypeUtils();
        this.messager = env.getMessager();
        this.filer = env.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        Set<? extends Element> metas = roundEnv.getElementsAnnotatedWith(PluginMeta.class);
        if (metas.isEmpty() || generated) {
            return false;
        }
        if (metas.size() > 1) {
            for (Element m : metas) {
                error(m, "Only one type may be annotated with @PluginMeta per project; found " + metas.size() + ".");
            }
            return false;
        }

        TypeElement main = (TypeElement) metas.iterator().next();
        if (!validateMain(main)) {
            return false;
        }

        generated = true;
        generate(main);
        return false;
    }

    private void generate(TypeElement main) {
        String mainBinary = elements.getBinaryName(main).toString();
        writeDescriptor(main, mainBinary);
    }

    private void writeDescriptor(TypeElement main, String mainBinary) {
        PluginMeta meta = main.getAnnotation(PluginMeta.class);
        StringBuilder y = new StringBuilder();
        scalar(y, "name", meta.name());
        scalar(y, "main", mainBinary);
        scalar(y, "version", meta.version());
        list(y, "api", meta.api());
        if (meta.authors().length > 0) {
            list(y, "authors", meta.authors());
        }
        if (!meta.description().isEmpty()) {
            scalar(y, "description", meta.description());
        }
        if (!meta.website().isEmpty()) {
            scalar(y, "website", meta.website());
        }
        scalar(y, "prefix", meta.prefix().isEmpty() ? meta.name() : meta.prefix());
        if (meta.order() != PluginLoadOrder.POSTWORLD) {
            y.append("load: ").append(meta.order().name()).append('\n');
        }
        String[] depend = Arrays.stream(meta.depend()).map(Dependency::name).toArray(String[]::new);
        String[] softDepend = Arrays.stream(meta.softDepend()).map(Dependency::name).toArray(String[]::new);
        if (meta.depend().length > 0) {
            list(y, "depend", depend);
        }
        if (meta.softDepend().length > 0) {
            list(y, "softdepend", softDepend);
        }
        if (meta.loadBefore().length > 0) {
            list(y, "loadbefore", meta.loadBefore());
        }

        try {
            FileObject yml = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "plugin.yml", main);
            try (Writer w = yml.openWriter()) {
                w.write(y.toString());
            }
        } catch (IOException ex) {
            error(main, "Failed to generate plugin.yml: " + ex.getMessage());
        }
    }

    private static void scalar(StringBuilder y, String key, String value) {
        y.append(key).append(": ").append(quote(value)).append('\n');
    }

    private static void list(StringBuilder y, String key, String[] values) {
        y.append(key).append(":\n");
        for (String v : values) {
            y.append("  - ").append(quote(v)).append('\n');
        }
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private boolean validateMain(TypeElement main) {
        if (main.getKind() != ElementKind.CLASS) {
            error(main, "@PluginMeta may only be placed on a class.");
            return false;
        }
        if (!isAssignableTo(main, "cn.nukkit.plugin.PluginBase")) {
            error(main, "@PluginMeta class must extend cn.nukkit.plugin.PluginBase.");
            return false;
        }
        return true;
    }

    private void error(Element e, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, e);
    }

    private boolean isAssignableTo(TypeElement type, String superFqn) {
        TypeElement superType = elements.getTypeElement(superFqn);
        if (superType == null) {
            return false;
        }
        TypeMirror erasedSuper = types.erasure(superType.asType());
        return types.isAssignable(types.erasure(type.asType()), erasedSuper);
    }
}
