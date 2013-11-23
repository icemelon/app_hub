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
import soot.SootMethodRef;
import soot.Transform;
import soot.Type;
import soot.Unit;
import soot.UnitBox;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.Constant;
import soot.jimple.Expr;
import soot.jimple.FieldRef;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.ParameterRef;
import soot.jimple.StringConstant;
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

	//public static final String android_jars_dir = "/Users/haichen/repo/soot/android-platforms";
	public static final String hubdroid_jar = "libs/hubdroid.jar";
	
	public static final String SensorClassName = "android.hardware.Sensor";
	public static final String SensorManagerClassName = "android.hardware.SensorManager";
	public static final String SensorHubManagerClassName = "android.hardware.SensorHubManager";
	public static final String SensorEventListenerClassName = "android.hardware.SensorEventListener";
	public static final String WakeLockClassName = "android.os.PowerManager$WakeLock";
	public static final String HandlerClassName = "android.os.Handler";
	
	public static final String GetSystemServiceMethod = "java.lang.Object getSystemService(java.lang.String)";
	public static final String RegisterListener = "boolean registerListener(" + SensorEventListenerClassName + "," + SensorClassName + ",int)";
	public static final String RegisterListenerWithHandler = "boolean registerListener(" + SensorEventListenerClassName 
			+ "," + SensorClassName + ",int," + HandlerClassName + ")";
	public static final String UnregisterListener = "void unregisterListener(" + SensorEventListenerClassName + ")";
	public static final String UnregisterListenerSensor = "void unregisterListener(" + SensorEventListenerClassName 
			+ "," + SensorClassName + ")";
	
	public static final String HubRegisterListener = "boolean registerListener(" + SensorEventListenerClassName + "," + SensorClassName + ",int,int)";
	
	public static final String hubManagerFieldName = "mSensorHubManager";
	
	private static Main instance = new Main();
	
	public static Main v() {
		return instance;
	}
	
	class PatchingUnit {
		private final Unit point;
		private final Body body;
		private List<Unit> beforeList = new ArrayList<Unit>();
		private List<Unit> afterList = new ArrayList<Unit>();
		private boolean bInsert = false;
		private boolean bRemove = false;
		
		public PatchingUnit(Unit point, Body body) {
			this.point = point;
			this.body = body;
		}
		
		public void insertBefore(Unit unit) {
			beforeList.add(unit);
			bInsert = true;
		}
		
		public void insertAfter(Unit unit) {
			afterList.add(unit);
			bInsert = true;
		}
		
		public void remove() {
			bRemove = true;
		}
		
		public void patch() {
			PatchingChain<Unit> units = body.getUnits();
			
			if (bInsert) {
				Log.println("[Patch Point] " + point);
				for (Unit unit: beforeList) {
					Log.println("  [Before] " + unit);
					units.insertBefore(unit, point);
				}
				
				Unit nextPoint = units.getSuccOf(point);
				for (Unit unit: afterList) {
					Log.println("  [After] " + unit);
					units.insertBefore(unit, nextPoint);
				}
			}
			
			if (bRemove) {
				Log.println("[Remove] " + point);
				units.remove(point);
			}
		}
	}
	
	private Chain<SootClass> appClassChain;
	private Chain<SootClass> libClassChain;
	private Chain<SootClass> phantomClassChain;
	private SootClass hubManagerClass = null;
	
	private Main() {
	}
	
	private void addSensorHubField() {
		
		for (SootClass sootClass: appClassChain) {
			SootField sensorManagerField = null;
			for (SootField field: sootClass.getFields())
				if (field.getType().toString().equals(Main.SensorManagerClassName)) {
					sensorManagerField = field;
					System.out.println(Modifier.toString(field.getModifiers()) + " " + field.getSignature());
					break;
				}
			
			if (sensorManagerField != null) {
				SootField hubField = new SootField(Main.hubManagerFieldName, RefType.v(hubManagerClass), sensorManagerField.getModifiers());
				sootClass.addField(hubField);
				Log.println("[Add Field] " + Modifier.toString(hubField.getModifiers()) + " " + hubField.getSignature() + ":" + hubField.getName());
			}
		}
	}
	
	private void instrument() {
		for (SootClass sootClass: appClassChain)
			for (SootMethod m: sootClass.getMethods())
				if (!Modifier.isAbstract(m.getModifiers()) &&
					!Modifier.isAbstract(m.getModifiers())) {
					Body body = m.retrieveActiveBody();
					PatchingChain<Unit> unitChain = body.getUnits();
					List<PatchingUnit> patchList = new ArrayList<PatchingUnit>();
					for (Unit unit: unitChain)
						if (unit instanceof InvokeStmt) {
							InvokeExpr invoke = ((InvokeStmt) unit).getInvokeExpr();
							if (invoke instanceof VirtualInvokeExpr) {
								SootMethod methodCall = invoke.getMethod();
								Value base = ((VirtualInvokeExpr) invoke).getBase();
								
								if (base.getType().toString().equals(Main.SensorManagerClassName)) {
									/*if (methodCall.getSubSignature().equals(Main.RegisterListener)) {
										Unit assignUnit = unitChain.getPredOf(unit);
										Value instance = null;
										while (assignUnit != null) {
											if (assignUnit instanceof AssignStmt && ((AssignStmt) assignUnit).getLeftOp().equals(base)) {
												InstanceFieldRef fieldRef = (InstanceFieldRef) ((AssignStmt) assignUnit).getRightOp();
												instance = fieldRef.getBase();
												break;
											}
											assignUnit = unitChain.getPredOf(assignUnit);
										}
										
										SootClass containingClass = ((RefType) instance.getType()).getSootClass();
										SootField hubField = containingClass.getFieldByName(Main.hubManagerFieldName);
										PatchingUnit patchUnit = new PatchingUnit(unit, body);
										
										Local hubLocal = Jimple.v().newLocal("$hub1", RefType.v(hubManagerClass));
										body.getLocals().add(hubLocal);
										AssignStmt newAssignStmt = Jimple.v().newAssignStmt(hubLocal, 
												Jimple.v().newInstanceFieldRef(instance, hubField.makeRef()));
										patchUnit.insertBefore(newAssignStmt);
										
										SootMethod method = hubManagerClass.getMethod(Main.HubRegisterListener);
										List<Value> args = new ArrayList<Value>();
										args.addAll(invoke.getArgs()); 
										args.add(IntConstant.v(Option.getBufferNum()));
										InvokeExpr newInvokeExpr = Jimple.v().newVirtualInvokeExpr(hubLocal, method.makeRef(), args);
										InvokeStmt newInvokeStmt = Jimple.v().newInvokeStmt(newInvokeExpr);
										patchUnit.insertBefore(newInvokeStmt);
										
										patchUnit.remove();
										
										patchList.add(patchUnit);
										
									} else if (methodCall.getSubSignature().equals(Main.RegisterListenerWithHandler)) {
										System.out.println("[ERROR] Unsupported API: " + methodCall.getSignature());
									} else if (methodCall.getSubSignature().equals(Main.UnregisterListener)) {
										Unit assignUnit = unitChain.getPredOf(unit);
										Value instance = null;
										while (assignUnit != null) {
											if (assignUnit instanceof AssignStmt && ((AssignStmt) assignUnit).getLeftOp().equals(base)) {
												InstanceFieldRef fieldRef = (InstanceFieldRef) ((AssignStmt) assignUnit).getRightOp();
												instance = fieldRef.getBase();
												break;
											}
											assignUnit = unitChain.getPredOf(assignUnit);
										}
										
										SootClass containingClass = ((RefType) instance.getType()).getSootClass();
										SootField hubField = containingClass.getFieldByName(Main.hubManagerFieldName);
										PatchingUnit patchUnit = new PatchingUnit(unit, body);
										
										Local hubLocal = Jimple.v().newLocal("$hub2", RefType.v(hubManagerClass));
										body.getLocals().add(hubLocal);
										AssignStmt newAssignStmt = Jimple.v().newAssignStmt(hubLocal, 
												Jimple.v().newInstanceFieldRef(instance, hubField.makeRef()));
										patchUnit.insertBefore(newAssignStmt);
										
										SootMethod method = hubManagerClass.getMethod(Main.UnregisterListener);
										InvokeExpr newInvokeExpr = Jimple.v().newVirtualInvokeExpr(hubLocal, method.makeRef(), invoke.getArg(0));
										InvokeStmt newInvokeStmt = Jimple.v().newInvokeStmt(newInvokeExpr);
										patchUnit.insertBefore(newInvokeStmt);
										
										patchUnit.remove();
										
										patchList.add(patchUnit);
									} else if (methodCall.getSubSignature().equals(Main.UnregisterListenerSensor)) {
										Unit assignUnit = unitChain.getPredOf(unit);
										Value instance = null;
										while (assignUnit != null) {
											if (assignUnit instanceof AssignStmt && ((AssignStmt) assignUnit).getLeftOp().equals(base)) {
												InstanceFieldRef fieldRef = (InstanceFieldRef) ((AssignStmt) assignUnit).getRightOp();
												instance = fieldRef.getBase();
												break;
											}
											assignUnit = unitChain.getPredOf(assignUnit);
										}
										
										SootClass containingClass = ((RefType) instance.getType()).getSootClass();
										SootField hubField = containingClass.getFieldByName(Main.hubManagerFieldName);
										PatchingUnit patchUnit = new PatchingUnit(unit, body);
										
										Local hubLocal = Jimple.v().newLocal("$hub2", RefType.v(hubManagerClass));
										body.getLocals().add(hubLocal);
										AssignStmt newAssignStmt = Jimple.v().newAssignStmt(hubLocal, 
												Jimple.v().newInstanceFieldRef(instance, hubField.makeRef()));
										patchUnit.insertBefore(newAssignStmt);
										
										SootMethod method = hubManagerClass.getMethod(Main.UnregisterListenerSensor);
										InvokeExpr newInvokeExpr = Jimple.v().newVirtualInvokeExpr(hubLocal, method.makeRef(), invoke.getArg(0), invoke.getArg(1));
										InvokeStmt newInvokeStmt = Jimple.v().newInvokeStmt(newInvokeExpr);
										patchUnit.insertBefore(newInvokeStmt);
										
										patchUnit.remove();
										
										patchList.add(patchUnit);
									} else {
										System.out.println("[WARNING] Undefined API: " + methodCall.getSignature());
									}*/
								} else if (base.getType().toString().equals(Main.WakeLockClassName)) {
									PatchingUnit patchUnit = new PatchingUnit(unit, body);
									patchUnit.remove();
									patchList.add(patchUnit);
								}
							}
						} /*else if (unit instanceof AssignStmt) {
							Value rhs = ((AssignStmt) unit).getRightOp();
							if (rhs instanceof VirtualInvokeExpr) {
								VirtualInvokeExpr invoke = (VirtualInvokeExpr) rhs; 
								if (invoke.getMethod().getSubSignature().equals(Main.GetSystemServiceMethod)) {
									if (((StringConstant) invoke.getArg(0)).value.equals("sensor")) {
										Unit assignUnit = unitChain.getSuccOf(unit);
										Value instance = null;
										while (assignUnit != null) {
											if (assignUnit instanceof AssignStmt) {
												Value lhs = ((AssignStmt) assignUnit).getLeftOp();
												if (lhs instanceof InstanceFieldRef && lhs.getType().toString().equals(Main.SensorManagerClassName)) {
													instance = ((InstanceFieldRef) lhs).getBase();
													break;
												}
											}
											assignUnit = unitChain.getSuccOf(assignUnit);
										}
										
										PatchingUnit patchUnit = new PatchingUnit(assignUnit, body);
										Local objLocal = Jimple.v().newLocal("$obj", RefType.v("java.lang.Object"));
										Local hubLocal = Jimple.v().newLocal("$hub", RefType.v(hubManagerClass));
										body.getLocals().add(objLocal);
										body.getLocals().add(hubLocal);
										
										InvokeExpr newInvokeExpr = Jimple.v().newVirtualInvokeExpr((Local) invoke.getBase(), 
												invoke.getMethodRef(), StringConstant.v("sensorhub"));
										AssignStmt newAssignStmt = Jimple.v().newAssignStmt(objLocal, newInvokeExpr);
										patchUnit.insertAfter(newAssignStmt);
										
										CastExpr newCastExpr = Jimple.v().newCastExpr(objLocal, RefType.v(hubManagerClass));
										newAssignStmt = Jimple.v().newAssignStmt(hubLocal, newCastExpr);
										patchUnit.insertAfter(newAssignStmt);
										
										SootClass containingClass = ((RefType) instance.getType()).getSootClass();
										SootField hubField = containingClass.getFieldByName(Main.hubManagerFieldName);
										newAssignStmt = Jimple.v().newAssignStmt(
												Jimple.v().newInstanceFieldRef(instance, hubField.makeRef()), hubLocal);
										patchUnit.insertAfter(newAssignStmt);
										
										patchList.add(patchUnit);
									}
								}
							}
						}*/
					
					if (!patchList.isEmpty()) {
						Log.println("---- Patch method: " + m.getSignature() + "----");
						for (PatchingUnit patchUnit: patchList)
							patchUnit.patch();
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
		
		Scene.v().addBasicClass(Main.SensorHubManagerClassName, SootClass.SIGNATURES);
		Scene.v().loadNecessaryClasses();
		PackManager.v().runPacks();
		System.out.println("----------------------- converting apk to Jimple ends ----------------------");
		
		appClassChain = Scene.v().getApplicationClasses();
		/*for (SootClass c: appClassChain) {
			Log.println("App class " + c.getName() + " level: " + c.resolvingLevel());// + " " + c.getInterfaceCount());
		}*/
		
		libClassChain = Scene.v().getLibraryClasses();
		for (SootClass c: libClassChain)
			if (c.getName().equals(Main.SensorHubManagerClassName)) {
				hubManagerClass = c;
				break;
			}
		/*for (SootClass c: libClassChain) {
			Log.println("Lib class " + c.getName() + " level: " + c.resolvingLevel());
		}*/
		
		phantomClassChain = Scene.v().getPhantomClasses();
		/*for (SootClass c: phantomClassChain)
			Log.println("Phantom class " + c.getName() + " level: " + c.resolvingLevel());*/
		
//		addSensorHubField();
		instrument();
		
		System.out.println("Start to output Jimple code.");
		Option.clearDirectory(Option.getOutputDir());
		PackManager.v().writeOutput();
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
