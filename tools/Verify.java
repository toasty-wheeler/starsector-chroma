import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Loads and initializes every class in the jar so the JVM verifies Janino's bytecode. */
public class Verify {
    public static void main(String[] args) throws Exception {
        URL[] urls = new URL[args.length];
        for (int i = 0; i < args.length; i++) urls[i] = new File(args[i]).toURI().toURL();
        URLClassLoader cl = new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent());
        JarFile jf = new JarFile(args[0]);
        Enumeration en = jf.entries();
        int ok = 0;
        while (en.hasMoreElements()) {
            JarEntry e = (JarEntry) en.nextElement();
            String n = e.getName();
            if (!n.endsWith(".class")) continue;
            String cn = n.substring(0, n.length() - 6).replace('/', '.');
            try {
                Class c = Class.forName(cn, true, cl);
                c.getDeclaredMethods();
                ok++;
            } catch (Throwable t) {
                System.out.println("FAIL " + cn + ": " + t);
            }
        }
        System.out.println("verified " + ok + " classes");
    }
}
