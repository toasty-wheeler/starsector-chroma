
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.codehaus.janino.CachingJavaSourceClassLoader;

/**
 * Builds Star Chroma with only the game's own Java runtime and the Janino compiler it ships with:
 *   1. compiles src/ into build/classes
 *   2. packs those classes into jars/StarChroma.jar
 *
 * Usage: Build <mod root> <starsector-core dir> <extra jar>...
 */
public class Build {

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        File core = new File(args[1]);


        ArrayList urls = new ArrayList();
        String[] coreJars = new String[] {"starfarer.api.jar", "lwjgl.jar", "lwjgl_util.jar", "log4j-1.2.9.jar", "json.jar", "xstream-1.4.10.jar"};
        for (int i = 0; i < coreJars.length; i++) urls.add(new File(core, coreJars[i]).toURI().toURL());
        for (int i = 2; i < args.length; i++) urls.add(new File(args[i]).toURI().toURL());
        URL[] ua = new URL[urls.size()];
        for (int i = 0; i < ua.length; i++) ua[i] = (URL) urls.get(i);
        URLClassLoader parent = new URLClassLoader(ua, ClassLoader.getSystemClassLoader().getParent());

        File src = new File(root, "src");
        File classes = new File(root, "build/classes");
        deleteTree(classes);
        classes.mkdirs();

        CachingJavaSourceClassLoader cl = new CachingJavaSourceClassLoader(parent, new File[] {src}, null, classes);
        ArrayList names = new ArrayList();
        collect(src, "", names);
        for (int i = 0; i < names.size(); i++) {
            String n = (String) names.get(i);
            cl.loadClass(n);
            System.out.println("compiled " + n);
        }

        File jar = new File(root, "jars/StarChroma.jar");
        jar.getParentFile().mkdirs();
        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getMainAttributes().putValue("Created-By", "Janino (Starsector bundled)");
        JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), mf);
        int count = addTree(out, classes, "");
        out.close();
        System.out.println("wrote " + jar + " (" + count + " classes)");
    }


    private static void collect(File dir, String pkg, ArrayList names) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                collect(f, pkg + f.getName() + ".", names);
            } else if (f.getName().endsWith(".java")) {
                names.add(pkg + f.getName().substring(0, f.getName().length() - 5));
            }
        }
    }

    private static int addTree(JarOutputStream out, File dir, String prefix) throws Exception {
        int n = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                n += addTree(out, f, prefix + f.getName() + "/");
            } else {
                out.putNextEntry(new JarEntry(prefix + f.getName()));
                FileInputStream in = new FileInputStream(f);
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                in.close();
                out.closeEntry();
                n++;
            }
        }
        return n;
    }

    private static void deleteTree(File f) {
        if (!f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) deleteTree(kids[i]);
        }
        f.delete();
    }
}
