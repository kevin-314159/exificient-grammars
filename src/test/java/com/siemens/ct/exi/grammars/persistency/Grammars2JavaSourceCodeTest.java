package com.siemens.ct.exi.grammars.persistency;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.junit.Test;

import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.grammars.SchemaInformedGrammars;
import com.siemens.ct.exi.grammars.XSDGrammarsBuilder;

public class Grammars2JavaSourceCodeTest extends TestCase {

	static String USER_HOME = System.getProperty("user.home");
	static String TMP_DIR = System.getProperty("java.io.tmpdir");
	static String WORKSPACE_DIR = System.getProperty("workspace");
	static String FILE_SEPARATOR = System.getProperty("file.separator");
	static String OS = System.getProperty("os.name").toLowerCase();

	static final String EXIFICIENT_CORE_JAR = "/.m2/repository/com/objsys/exi/exificient-core/1.0.0-SNAPSHOT/exificient-core-1.0.0-SNAPSHOT.jar";

	XSDGrammarsBuilder grammarBuilder = XSDGrammarsBuilder.newInstance();

	public Grammars2JavaSourceCodeTest() throws EXIException {
		super();
	}

	protected void _test(String xsd) throws EXIException, IOException {
		grammarBuilder.loadGrammars(xsd);
		SchemaInformedGrammars grammars = grammarBuilder.toGrammars();

		Grammars2JavaSourceCode g2j = new Grammars2JavaSourceCode(grammars);
		g2j.generateCode();
		String packageName = Grammars2JavaSourceCode.class.getPackage()
				.toString();
		String className = "Test" + System.currentTimeMillis();

		String sSource = g2j.getGrammars(packageName, className);

		// further validation (try to compile Java Code)
		String sTempDir = getTemporaryFolder("javaSource_");
		String jpath = packageName.replace("package ", ""); // replace leading
															// "package ";
		jpath = jpath.replaceAll("\\.", "/"); // replace all dots with file
												// separator
		File f = new File(sTempDir + jpath + "/" + className + ".java");
		f.getParentFile().mkdirs();
		writeStringToFile(sSource, f);
		System.out.println("File " + f + " exists: " + f.exists());

		String sCmd = "javac -cp " + getEXIficientCoreJar() + " " + sTempDir
				+ jpath + "/" + "*.java";
		System.out.println("CMD: " + sCmd);

		ProcessBuilder builder;
		if (isWindows()) {
			builder = new ProcessBuilder("javac", "-cp",
					getEXIficientCoreJar(), sTempDir + jpath + "/" + "*.java");
		} else if (isUnix()) {
			builder = new ProcessBuilder("/bin/sh", "-c", sCmd);
		} else {
			throw new RuntimeException("Unsupported operating system: " + OS);
		}
		final Process process = builder.start();

		// read streams
		String errMsg = getMsg(process.getErrorStream());
		String inpMsg = getMsg(process.getInputStream());

		if (errMsg == null || errMsg.startsWith("Picked up _JAVA_OPTIONS")) {
			// NO Error
			// Travis seems to report always
			// "Picked up _JAVA_OPTIONS: -Xmx2048m -Xms512m"
		} else {
			if (inpMsg != null) {
				errMsg += "\nCompilerMsg:\n" + inpMsg;
			}
			fail(errMsg);
		}
	}

	static String getEXIficientCoreJar() {
		// String url =
		// "https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.siemens.ct.exi&a=exificient-core&v=LATEST";
		final String DEFAULT_EXIFICIENT_CORE_JAR = USER_HOME + EXIFICIENT_CORE_JAR;

		return DEFAULT_EXIFICIENT_CORE_JAR;
	}

	static void writeStringToFile(String str, File file) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.write(str);
		writer.close();
	}

	protected static String getMsg(InputStream is) throws IOException {
		int b;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		while ((b = is.read()) != -1) {
			baos.write(b);
		}

		String msg = null;
		if (baos.size() > 0) {
			msg = new String(baos.toByteArray());
		}

		baos.close();
		return msg;
	}

	public static boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public static boolean isUnix() {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS
				.indexOf("aix") > 0);
	}

	public static boolean isMac() {
		return (OS.indexOf("mac") >= 0);
	}

	public static boolean isSolaris() {

		return (OS.indexOf("sunos") >= 0);
	}

	String getTemporaryFolder(String prefix) throws IOException {
		String dirName = TMP_DIR; // + FILE_SEPARATOR; //
									// System.getProperty("java.io.tmpdir");
		// String dirName = "D:" + FILE_SEPARATOR + "EXI_TMP" ;
		dirName += FILE_SEPARATOR + prefix + System.currentTimeMillis()
		// + FILE_SEPARATOR
		;

		File f = new File(dirName);

		if (!f.mkdirs()) {
			throw new RuntimeException("Error while creating temp folder: "
					+ dirName);
		}

		return dirName;
	}

	@Test
	public void testNotebook() throws EXIException, IOException {
		String xsd = "data/W3C/PrimerNotebook/notebook.xsd";
		_test(xsd);
	}

	@Test
	public void testEXIForJSON() throws EXIException, IOException {
		String xsd = "data/W3C/EXIforJSON/exi4json.xsd";
		_test(xsd);
	}

	@Test
	public void testPull5() throws Exception {
		// very special case: same attribute with different type --> represented
		// as a String (default string)
		String xsd = "data/general/pull5.xsd";
		_test(xsd);
	}

	@Test
	public void testGaml100() throws EXIException, IOException {
		String xsd = "data/Gaml/gaml100.xsd";
		_test(xsd);
	}

}
