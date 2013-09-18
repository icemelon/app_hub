package main;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;

import soot.ArrayType;
import soot.Body;
import soot.G;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.PhaseOptions;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.Transform;
import soot.Type;
import soot.Unit;
import soot.UnitBox;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.Constant;
import soot.jimple.Expr;
import soot.jimple.FieldRef;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.JimpleBody;
import soot.jimple.ParameterRef;
import soot.jimple.ThisRef;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;
import soot.shimple.ShimpleBody;
import soot.tagkit.GenericAttribute;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.BriefBlockGraph;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;

public class Main {

	public static final String StringClassName = "java.lang.String";
	public static final String StringBuilderClassName = "java.lang.StringBuilder";
	public static final String SensorEventClassName = "android.hardware.SensorEvent";
	public static final String SensorManagerClassName = "android.hardware.SensorManager";
	public static final String HandlerMessageClassName = "android.os.Message";
	public static final String SensorCallbackFunc1 = "void onSensorChanged(" + SensorEventClassName + ")";
	public static final String SensorCallbackFunc2 = "void onSensorChanged(int,float[])";
	public static final String android_jars_dir = "/Users/haichen/repo/soot/android-platforms";
	public static final String hubdroid_jar = "libs/hubdroid.jar";
	
	private static Main instance = new Main();
	
	public static Main v() {
		return instance;
	}
	
	private Chain<SootClass> appClassChain;
	private Chain<SootClass> libClassChain;
	private Chain<SootClass> phantomClassChain;
	
	private Main() {
	}
	
	private void search() {
		for (SootClass sootClass: appClassChain)
			for (SootMethod method: sootClass.getMethods()) {
				if (!Modifier.isAbstract(method.getModifiers()) &&
					!Modifier.isAbstract(method.getModifiers())) {
					Body body = method.retrieveActiveBody();
					for (Unit unit: body.getUnits()) {
						if (unit instanceof InvokeStmt) {
							InvokeExpr invoke = ((InvokeStmt) unit).getInvokeExpr();
							if (invoke instanceof VirtualInvokeExpr) {
								Value base = ((VirtualInvokeExpr) invoke).getBase();
								if (base.getType().equals(Main.SensorManagerClassName)) {
									System.out.println(unit);
								}
							}
						}
					}
				}
			}
	}
	
	public void run() {
		
		List<String> process_dir = new LinkedList<String>();
		
		process_dir.add(Option.getApk());
		Options.v().set_process_dir(process_dir);
		Options.v().set_src_prec(Options.src_prec_apk);
		Options.v().set_output_format(Options.output_format_J);
		Options.v().set_allow_phantom_refs(true);
		Options.v().force_android_jar();
		Options.v().set_force_android_jar(hubdroid_jar);
		Options.v().set_soot_classpath(Option.getApk() + ":" + hubdroid_jar);
		Options.v().set_output_dir(Option.getOutputDir());
		
		Scene.v().loadNecessaryClasses();
		PackManager.v().runPacks();
		PackManager.v().writeOutput();
		
		System.out.println("----------------------- converting apk to Jimple ends ----------------------");
		
		appClassChain = Scene.v().getApplicationClasses();
		/*for (SootClass c: appClassChain) {
			Log.println("App class " + c.getName() + " level: " + c.resolvingLevel());// + " " + c.getInterfaceCount());
			if (c.getName().equals("dalvik.system.Taint") || c.getName().equals("name.bagi.levente.pedometer.StepDetector"))
				debugSootClass(c);
		}
		updateClassDefInfo(appClassChain);*/
		
		libClassChain = Scene.v().getLibraryClasses();
		/*for (SootClass c: libClassChain) {
			Log.println("Lib class " + c.getName() + " level: " + c.resolvingLevel());
			if (c.getName().equals("android.content.ContextWrapper"))
				debugSootClass(c);
		}
		updateClassDefInfo(libClassChain);*/
		
		phantomClassChain = Scene.v().getPhantomClasses();
		/*for (SootClass c: phantomClassChain)
			Log.println("Phantom class " + c.getName() + " level: " + c.resolvingLevel());
		updateClassDefInfo(phantomClassChain);*/
		
		
		/*try {
			ClassHashTable.generateHierarchy();
		} catch (ClassInfoNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		}*/
		search();
		
		/*
		System.out.println("Start to output Jimple code.");
		Option.clearDirectory(Option.getOutputDir());
		
		Options.v().set_output_dir(Option.getOutputDir());
		Options.v().set_output_format(Options.output_format_J);
		PackManager.v().writeOutput();*/
	}
	
	public static void main(String[] args) {
		
		Log.setDebug(true);
		try {
			PrintStream output = new PrintStream(new File("log.txt"));
			Log.setOutputStream(output);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (!Option.parseArgs(args)) {
			Option.usage();
			return;
		}
		
		Main m = Main.v();
		m.run();
	}
}
