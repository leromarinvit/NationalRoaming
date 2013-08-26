package net.volatilevoid.nationalroaming;

import static de.robv.android.xposed.XposedHelpers.*;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;

import java.lang.reflect.*;
import android.telephony.ServiceState;

public class NationalRoaming implements IXposedHookZygoteInit {

    private static final String TAG = "National Roaming: ";

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        final Method getprop = findMethodExact("android.os.SystemProperties", null, "get", String.class, String.class);

        findAndHookMethod(
                "com.android.internal.telephony.gsm.GsmServiceStateTracker",
                null,
                "pollStateDone",
                new XC_MethodHook() {

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        ServiceState newSS;
                        try {
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

                        if (sim.substring(0, 3).equals(net.substring(0, 3)))
                            newSS.setRoaming(false); // MCC is the same - we're at home
                    }
                }
                );
        XposedBridge.log(TAG + "hooked com.android.internal.telephony.gsm.GsmServiceStateTracker.pollStateDone");
    }

}
