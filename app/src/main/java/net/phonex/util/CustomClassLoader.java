package net.phonex.util;

/**
 * @author http://codeslices.net team
 */

import net.phonex.service.runEngine.LibraryLoader;

import net.phonex.xv.XviJNI;

import dalvik.system.PathClassLoader;

/**
 * Simple custom class loader implementation.
 * It is used to load native libraries in a separate ClassLoader.
 * Thus if this ClassLoader is garbage collected, libraries are
 * unloaded from the memory (this is the only way).
 * 
 * ClassLoaders are hierarchically structured. Each ClassLoader has
 * a parent. During loading a class, parent is queried at first.
 * If class is not found, current ClassLoader tries to load the class.
 * BootClassLoader is root of the hierarchy. 
 * 
 * Hierarchy:
 * 
 *                   BootClassLoader
 *                  /              \
 *                 /                \   
 *                /                  \  
 *    DexClassLoader <-reflection-> CustomClassLoader
 * 
 * 
 *  - DexClassLoader is the ClassLoader used for loading this application, 
 *      whole code is loaded using this class loader.
 *  - System classes (i.e., java.**) are loaded using BootClassLoader.
 *      It does not have access to the application classes stored in a DEX file.
 *  - CustomClassLoader is used to load only direct native-lib-handling classes,
 *      since native libs are loaded using ClassLoader which was used for loading
 *      a class that loaded native libs.
 *  - Classes can interact (i.e., casting) if they are loaded using common parent node
 *      somewhere in the hierarchy. Classes loaded by separated class loaders
 *      are different, even though they are loaded from the same code.
 *      Thus Callback loaded by DexClassLoader is different from the Callback
 *      loaded by CustomClassLoader.
 *      
 *  Special loading rule is applied here. Native-lib-handling classes are loaded
 *  using this ClassLoader. During this loading, classes contain another class references
 *  (imports in the classes), but those are loaded using DexClassLoader in order to make
 *  it possible to use application code to pass the objects to the JNI code (i.e., Callbacks).
 *  This is done by overriding loadClass() in this class loader which chooses which classloader
 *  will be used depending on the class name.
 *  
 *  Warning: Classes loaded by CustomClassLoader cannot use Log class. It does not work.
 *  There is a problem when pjsuaJNI code tries to access a code loaded by different class loader,
 *  <b>pre-verify</b> error occurs. Work around to avoid this is to avoid using non-system objects
 *  in pjsuaJNI, so instead of passing new pj_stun_nat_detect_result object, we pass long pointer
 *  (primitive, long) and object is reconstructed on the other side. 
 *  Serialization is another possible workaround.
 *  It seems that using reflection to call objects from another world also works.
 *  
 *  The main problem was in creating an objects from pjsuaJNI and passing them to the Callback object.
 *  This is done in pjsuaJNI methods like SwigDirector_MobileRegHandlerCallback_on_restore_contact.
 *  This can be done either by a) passing pointer and Callback reconstructs the object using JNI call
 *  or b) in case of returning a string, use a reflection:
 *  <pre>
 *	  Class<?> str = Class.forName(pj_str_t.class.getName());
 *    Method m = str.getMethod("getPtrx", new Class<?>[]{}); // new method to obtain pointer from existing object.
 *	  return (Long) m.invoke(res, (Object[]) null);
 *	</pre>  
 *  
 *  Note that the same class loaded by different class loaders is not compatible in them, so
 *  casting is not possible. But if they implement the same interface, they can be casted to 
 *  this interface. @see {http://www.codethesis.com/blog/unload-java-jni-dll}.
 *  
 *  Note that currently this code is not actively used since there is a memory leak
 *  when this ClassLoader directly loads (not using its parent) some class from a DEX file.
 *  Loaded class is not garbage collected and since it holds reference to its class loader it
 *  is not garbage collected either. The issue was described here: 
 *  https://stackoverflow.com/questions/24225572/android-classloader-memory-leak
 *  To reproduce the error it suffices to instantiate a class loader and call 
 *  its method loadClass() on a very trivial class in the project. Even if the class variable 
 *  is nulled, it still remains in the memory (in the thread maybe?).
 *  
 *  Usage:
 *  Context ctxt;
 *  PackageInfo pinfo = ctxt.getPackageManager().getPackageInfo(ctxt.getPackageName(), 0);
 *	classLoader = new CustomClassLoader(
 *			pinfo.applicationInfo.publicSourceDir,
 *          ctxt.getDir("outdex", Context.MODE_PRIVATE).getAbsolutePath(),
 *          pinfo.applicationInfo.nativeLibraryDir,
 *          ctxt.getClassLoader().getParent(),
 *          ctxt.getClassLoader());
 * 
 * @see {http://developer.classpath.org/doc/java/lang/ClassLoader-source.html}
 */
public class CustomClassLoader extends PathClassLoader { //DexClassLoader {
	private static final String THIS_FILE="CustomClassLoader";
	
	/**
	 * Application DexClassLoader.
	 */
	private final ClassLoader clRealParent;
	
    public CustomClassLoader(String dexPath, String optimizedDirectory, String libraryPath, ClassLoader parent, ClassLoader realParent) {
		//super(dexPath, optimizedDirectory, libraryPath, parent);
    	super(dexPath, libraryPath, parent);
		this.clRealParent = realParent;
		
		Log.vf(THIS_FILE, "Loader initialization finished, dexPath=[%s], optDir=[%s] libPath=[%s] this=[0x%x] parent=[0x%x] realParent=[0x%x]", 
				dexPath, optimizedDirectory, libraryPath, this.hashCode(), parent.hashCode(), realParent.hashCode());
	}
    
	@Override
	protected void finalize() throws Throwable {
		Log.vf(THIS_FILE, "Finalizing classloader [%s]", this.toString());
		super.finalize();
	}

	/**
     * The HashMap where the classes will be cached
     */
    //private final Map<String, Class<?>> classes = new HashMap<String, Class<?>>();

	@Override
	public String findLibrary(String name) {
		Log.vf(THIS_FILE, "findLibrary(%s)", name);
		return super.findLibrary(name);
	}
    
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Log.vf(THIS_FILE, "findClass(%s)", name);
		return super.findClass(name);
	}
	
	/**
	 * Determines whether the provided class should be loaded using external class loader.
	 * Class loading external libraries is here, so native lib is loaded using this classloader.
	 * Native JNI interface is here, so native libraries are only visible to this classloader.
	 * 
	 * @param className
	 * @return
	 */
	private boolean useThisToLoad(String className){
		return (   LibraryLoader.class.getName().equals(className)
		    	|| XviJNI.class.getName().equals(className)
				);
	}

	@Override
	public Class<?> loadClass(String className) throws ClassNotFoundException {
		Log.vf(THIS_FILE, "loadClass(%s)", className);
		
		/*if (classes.containsKey(className)) {
			Log.v(THIS_FILE, "return cached class " + className);
            return classes.get(className);
        }*/
		
		// Idea: all direct native-libraries-handling-classes are loaded using this
		// ClassLoader while another classes are loaded using application classloader.
		// This is important so JNI classes and application can share classes.
		if (useThisToLoad(className)){
			// Use this ClassLoader to load shared libraries.
			// They will be then loaded separately.
			Log.v(THIS_FILE, "Using myself to load the class.");
			return super.loadClass(className);
		}
		
		// Use real parent to load the class. It is the ClassLoader that is
		// used to load the whole application.
		return clRealParent.loadClass(className);
	}

	@Override
	protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
		Log.vf(THIS_FILE, "loadClass(%s, %s)", className, resolve);
		
		if (!resolve){
			if (useThisToLoad(className)){
				// Use this ClassLoader to load shared libraries.
				// They will be then loaded separately.
				Log.v(THIS_FILE, "Using myself to load the class.");
				return super.loadClass(className, resolve);
			}
			
			Log.v(THIS_FILE, "Using application class loader to load the class.");
			return clRealParent.loadClass(className);
		} else {
			Log.w(THIS_FILE, "Resolve is true....");
		}
		
		Class<?> ret = super.loadClass(className, resolve);
		return ret;
	}
}
