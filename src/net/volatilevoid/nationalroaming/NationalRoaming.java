package net.volatilevoid.nationalroaming;

import static de.robv.android.xposed.XposedHelpers.*;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.*;
import java.util.HashMap;
import android.telephony.ServiceState;
import android.os.Build;

public class NationalRoaming implements IXposedHookLoadPackage {

    private static final String TAG = "National Roaming: ";

    // based on http://www.insys-icom.com/mcc/
    public static final HashMap<String, String> baseCountryCodes = new HashMap<String, String>();
    static {
        baseCountryCodes.put("311", "310"); // USA
        baseCountryCodes.put("312", "310"); // USA
        baseCountryCodes.put("313", "310"); // USA
        baseCountryCodes.put("314", "310"); // USA
        baseCountryCodes.put("315", "310"); // USA
        baseCountryCodes.put("316", "310"); // USA
        baseCountryCodes.put("430", "424"); // United Arab Emirates
        baseCountryCodes.put("431", "424"); // United Arab Emirates
        baseCountryCodes.put("441", "440"); // Japan
        baseCountryCodes.put("461", "460"); // China
        baseCountryCodes.put("405", "404"); // India
        baseCountryCodes.put("235", "234"); // United Kingdom
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        
        if (!lpparam.packageName.equals("com.android.providers.telephony"))
            return;
        
        final Method getprop = findMethodExact("android.os.SystemProperties", null, "get", String.class, String.class);

        findAndHookMethod(
                "com.android.internal.telephony.gsm.GsmServiceStateTracker",
                lpparam.classLoader,
                "pollStateDone",
                new XC_MethodHook() {

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        ServiceState newSS;
                        try {
                            if (Build.VERSION.SDK_INT >= 18) // Android 4.3+
                                newSS = (ServiceState)getObjectField(param.thisObject, "mNewSS");
                            else
                                newSS = (ServiceState)getObjectField(param.thisObject, "newSS");
                        } catch (NoSuchFieldError e) {
                            XposedBridge.log(TAG + "newSS not found");
                            return;
                        }

                        if (newSS.getRoaming() == false)
                            return; // Android thinks we're not roaming - who are we to argue?

                        String prop = "gsm.sim.operator.numeric";
                        try {
                            // MTK dual SIM phones have mSimId and gsm.sim.*.2 
                            int mSimId = getIntField(param.thisObject, "mSimId");
                            if (mSimId != 0)
                                prop = "gsm.sim.operator.numeric.2";
                        } catch (NoSuchFieldError e) {}

                        String sim = (String)getprop.invoke(null, prop, "");
                        if (sim.equals("")) {
                            XposedBridge.log(TAG + prop + " is empty or doesn't exist");
                            return;
                        }

                        String net = newSS.getOperatorNumeric();

                        String simMcc = sim.substring(0,3);
                        String netMcc = net.substring(0,3);

                        if (simMcc.equals(netMcc) || baseMcc(simMcc).equals(baseMcc(netMcc)))
                            newSS.setRoaming(false); // MCC is the same - we're at home
                    }

                    protected String baseMcc(String mcc) {
                        String baseMcc = baseCountryCodes.get(mcc);
                        if (baseMcc != null) {
                                return baseMcc;
                        } else {
                                return mcc;
                        }
                    }
                }
                );
        XposedBridge.log(TAG + "hooked com.android.internal.telephony.gsm.GsmServiceStateTracker.pollStateDone");
    }

}
