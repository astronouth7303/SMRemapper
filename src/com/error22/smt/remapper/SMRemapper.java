package com.error22.smt.remapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import com.error22.smt.remapper.parser.AstralMapLexer;
import com.error22.smt.remapper.parser.AstralMapListener;
import com.error22.smt.remapper.parser.AstralMapParser;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import com.google.common.io.Files;

public class SMRemapper extends Remapper {
	public static final int CLASS_LENGTH = ".class".length();
	private BiMap<String, String> classMap;
	private Map<String, ClassNode> classNodeMap;
	private Map<StringTriple, StringTriple> fieldMap, methodMap;
	private Map<String, JarEntry> jarMap;
	private JarFile jar;
	private boolean keepSource;
	private ILog log;

	public SMRemapper(ILog log) {
		this.log = log;
		classMap = new HashBiMap<String, String>.create();
		fieldMap = new HashMap<>();
		methodMap = new HashMap<>();
		classNodeMap = new HashMap<>();
		jarMap = new HashMap<>();
	}

	/**
	 * Fully resets the remapper as if it has not been used.
	 */
	public void reset() {
		resetMappings();
		resetClasses();
	}

	/**
	 * Resets any mappings loaded by loadMapping(...)
	 */
	public void resetMappings() {
		classMap.clear();
		fieldMap.clear();
		methodMap.clear();
	}

	/**
	 * Resets all class data caused by laodLib(...) or remap(...)
	 */
	public void resetClasses() {
		classNodeMap.clear();
		jarMap.clear();
		jar = null;
	}

	/**
	 * Loads the mappings, it can also reverse them. You can load multiple
	 * mapping files, it will auto overwrite existing rules. A possible use
	 * would be to load a raw_min file and then load a custom community file.
	 * 
	 * @param mapping
	 *            The file to load
	 * @param reverse
	 *            If the mappings should be reversed
	 * @throws IOException
	 *             Normally if the mapping could not be read
	 */
	public void loadMapping(File mapping, boolean reverse) throws IOException {
		log.log("Loading mappings...");

		// Parse with ANTLR
		ANTLRInputStream input = new ANTLRFileStream(mapping.getAbsolutePath(), "UTF-8");
		AstralMapLexer lexer = new AstralMapLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		AstralMapParser parser = new AstralMapParser(tokens);
		AstralMapParser.MapFileContext mapfile = parser.mapFile();


	}

	/**
	 * Loads a library
	 * 
	 * @param path
	 *            The library to load
	 * @throws Exception
	 *             Normally if the library is empty or corrupt
	 */
	public void loadLib(File path) throws Exception {
		log.log("    Loading lib "+path.getPath()+"...");
		JarFile libJar = new JarFile(path, false);

		for (Enumeration<JarEntry> entr = libJar.entries(); entr.hasMoreElements();) {
			JarEntry entry = entr.nextElement();
			String name = entry.getName();

			if (entry.isDirectory()) {
				continue;
			}

			if (name.endsWith(".class")) {
				name = name.substring(0, name.length() - CLASS_LENGTH);

				ClassReader cr = new ClassReader(libJar.getInputStream(entry));
				ClassNode node = new ClassNode();
				cr.accept(node, 0);

				classNodeMap.put(name, node);
			}
		}

		libJar.close();
	}

	/**
	 * Remaps the input to the output
	 * 
	 * @param input
	 *            The file to use
	 * @param output
	 *            The non existent file to output to
	 * @throws Exception
	 *             Normally if something went seriously wrong
	 */
	public void remap(File input, File output) throws Exception {
		log.log("Remapping jar with " + classMap.size() + " class mappings, " + fieldMap.size() + " field mappings and "
				+ methodMap.size() + " method mappings");

		jar = new JarFile(input, false);

		JarOutputStream out = new JarOutputStream(new FileOutputStream(output));

		log.log("    First pass...");
		for (Enumeration<JarEntry> entr = jar.entries(); entr.hasMoreElements();) {
			JarEntry entry = entr.nextElement();
			String name = entry.getName();

			if (entry.isDirectory()) {
				continue;
			}

			if (name.endsWith(".class")) {
				name = name.substring(0, name.length() - CLASS_LENGTH);
				jarMap.put(name, entry);

				ClassReader cr = new ClassReader(jar.getInputStream(entry));
				ClassNode node = new ClassNode();
				cr.accept(node, 0);

				classNodeMap.put(name, node);
			} else {
				JarEntry nentry = new JarEntry(name);
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				int n;
				byte[] b = new byte[1 << 15];
				InputStream is = jar.getInputStream(entry);
				while ((n = is.read(b, 0, b.length)) != -1) {
					buffer.write(b, 0, n);
				}
				buffer.flush();

				nentry.setTime(0);
				out.putNextEntry(nentry);
				out.write(buffer.toByteArray());
			}
		}

		log.log("    Second pass...");
		for (Entry<String, JarEntry> e : jarMap.entrySet()) {

			ClassReader reader = new ClassReader(jar.getInputStream(e.getValue()));
			ClassNode node = new ClassNode();

			RemapperClassAdapter mapper = new RemapperClassAdapter(this, node);
			reader.accept(mapper, 0);

			ClassWriter wr = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(wr);

			JarEntry entry = new JarEntry(map(e.getKey()) + ".class");
			entry.setTime(0);
			out.putNextEntry(entry);
			out.write(wr.toByteArray());

		}

		jar.close();
		out.close();

		log.log("Complete!");
	}

	public void setKeepSource(boolean keepSource) {
		this.keepSource = keepSource;
	}

	public ILog getLog() {
		return log;
	}

	@Override
	public String map(String typeName) {
		if (classMap.containsKey(typeName)) {
			return classMap.get(typeName);
		}

		int index = typeName.lastIndexOf('$');
		if (index != -1) {
			String outer = typeName.substring(0, index);
			String mapped = map(outer);
			if (mapped == null)
				return null;
			return mapped + typeName.substring(index);
		}

		return typeName;
	}

	public ClassNode getClass(String clazz) {
		return classNodeMap.containsKey(clazz) ? classNodeMap.get(clazz) : null;
	}

	public String mapFieldName(String owner, String name, String desc, int access, boolean base) {
		StringTriple mapped = fieldMap.get(new StringTriple(owner, name, desc));
		ClassNode clazz = getClass(owner);

		if (mapped != null) {
			return mapped.getName();
		} else if (checkParents(access) && clazz != null) {
			if (clazz.superName != null) {
				String map = mapFieldName(clazz.superName, name, desc, access, false);
				if (map != null) {
					return map;
				}
			}

			for (String iface : clazz.interfaces) {
				String map = mapFieldName(iface, name, desc, access, false);
				if (map != null) {
					return map;
				}
			}
		}
		return base ? name : null;
	}

	public String mapMethodName(String owner, String name, String desc, int access, boolean base) {
		StringTriple mapped = methodMap.get(new StringTriple(owner, name, desc));
		ClassNode clazz = getClass(owner);

		if (mapped != null) {
			return mapped.getName();
		} else if (checkParents(access) && clazz != null) {
			if (clazz.superName != null) {
				String map = mapMethodName(clazz.superName, name, desc, access, false);
				if (map != null) {
					return map;
				}
			}

			for (String iface : clazz.interfaces) {
				String map = mapMethodName(iface, name, desc, access, false);
				if (map != null) {
					return map;
				}
			}
		}
		return base ? name : null;
	}

	private boolean checkParents(int access) {
		return access == -1 || (!Modifier.isPrivate(access) && !Modifier.isStatic(access));
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		return mapFieldName(owner, name, desc, 0, true);
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		return mapMethodName(owner, name, desc, 0, true);
	}

	public boolean shouldKeepSource() {
		return keepSource;
	}

	public static String getVersion() {
		return "1.2 Alpha";
	}

	public static void main(String[] args) throws Exception {
		System.out.println("SMRemapper - Version " + getVersion());
		System.out.println("Made for SMT by Error22");
		System.out.println();

		if (args.length != 6) {
			System.out.println(
					"Usage: java -jar SMRemapper.jar {input} {output} {mapping} {libs folder} {reverse (true/false)} {keep source (true/false)}");
			System.out.println(
					"Libs Folder: The libs folder must include the rt.jar(or classes on mac) file otherwise inheritance lookup will not work correctly!");
			System.out.println(
					"Remember: You will also need the StarMade.jar(or the deobf version) in the libs folder if you are only working with a partial jar (ie only contains changed files)");
			System.exit(0);
		}

		File input = new File(args[0]);
		File output = new File(args[1]);
		File mapping = new File(args[2]);
		File libsFolder = new File(args[3]);
		boolean reverse = args[4].equalsIgnoreCase("true");
		boolean keepSource = args[5].equalsIgnoreCase("true");

		SMRemapper instance = new SMRemapper(new ILog() {
			@Override
			public void log(String text) {
				System.out.println(text);
			}
		});

		instance.setKeepSource(keepSource);
		instance.loadMapping(mapping, reverse);

		if (libsFolder == null || !libsFolder.exists()) {
			System.out.println("Libs folder does not exist!");
			System.exit(0);
		}
		
		System.out.println("Loading libs...");
		for (File lib : libsFolder.listFiles()) {
			try {
				instance.loadLib(lib);
			} catch (Exception e) {
				e.printStackTrace();
				instance.getLog().log("Failed to load lib! " + lib.getPath() + " " + e.getMessage());
			}
		}

		instance.remap(input, output);
	}
}
