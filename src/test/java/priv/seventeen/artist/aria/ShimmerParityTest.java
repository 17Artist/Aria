package priv.seventeen.artist.aria;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler;
import priv.seventeen.artist.aria.annotation.java.AriaNamespace;
import priv.seventeen.artist.aria.annotation.java.AriaObjectConstructor;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.object.IAriaObject;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;

/**
 * Shimmer -> Aria 迁移的可疑差异实证测试。
 * 每条差异一个 @Test，用 System.out.println 打印实测行为，供从 TEST-*.xml 的 system-out 读取判定。
 * 只放在 test 目录，不改 Aria 主源码，不改 mod。
 */
public class ShimmerParityTest {

    @BeforeAll
    static void setup() {
        Aria.getEngine().initialize();
        // 注册对象(实例方法 + 构造器) —— 走 registerObject 路径(mod ShimmerEngine.registerShimmerObjects 同路径)
        CallableManager.INSTANCE.registerObject(ProbeObject.class);
        // 注册 target 对象函数(静态 @AriaInvokeHandler(target=...)) —— 走 registerObjectFunction(Class) 路径
        CallableManager.INSTANCE.registerObjectFunction(TargetFns.class);
    }

    // ============================================================
    // 1. 对象实例方法 arg/self 约定 (registerObject 路径)
    // ============================================================

    /** 供实例方法把观察到的 InvocationData 内容写回，测试线程读取。 */
    static volatile String INSTANCE_OBS = null;

    public static class ProbeObject implements IAriaObject {
        @AriaObjectConstructor("Probe")
        public ProbeObject(InvocationData data) {}

        @Override public String getTypeName() { return "Probe"; }

        @AriaInvokeHandler("probe")
        public IValue<?> probe(InvocationData data) {
            IValue<?> a0 = data.get(0);
            IValue<?> a1 = data.get(1);
            StringBuilder sb = new StringBuilder();
            sb.append("size=").append(data.size());
            sb.append(" get(0).class=").append(a0 == null ? "null" : a0.getClass().getSimpleName());
            sb.append(" get(0).str=").append(a0 == null ? "null" : a0.stringValue());
            sb.append(" get(1).class=").append(a1 == null ? "null" : a1.getClass().getSimpleName());
            sb.append(" get(1).str=").append(a1 == null ? "null" : a1.stringValue());
            sb.append(" target.class=").append(data.getTarget() == null ? "null" : data.getTarget().getClass().getSimpleName());
            INSTANCE_OBS = sb.toString();
            return NoneValue.NONE;
        }
    }

    @Test
    void t01_instanceMethodSelfInArgs() throws AriaException {
        INSTANCE_OBS = null;
        Context ctx = new Context(new GlobalStorage());
        // 脚本: 构造对象 -> 调用带一个用户参数 42 的实例方法
        Aria.eval("var.o = Probe()\no.probe(42)\n", ctx);
        System.out.println("[1][instance-method] 用户调用 o.probe(42) 时实例方法看到: " + INSTANCE_OBS);
        System.out.println("[1] 期望(Shimmer): size=1, get(0)=42(NumberValue), target=self");
        System.out.println("[1] 若 Aria self-in-args[0]: size=2, get(0)=Probe(ObjectValue/self), get(1)=42");
    }

    // ============================================================
    // 2. target 对象函数 arg 约定 (registerObjectFunction(Class) 路径)
    // ============================================================

    /** 模拟被 target 的 JVM 类型(如 ItemStack)。Aria 需要它是某个 IValue 包着的对象；
     *  这里直接把 TargetHolder 注册为一个 IAriaObject，方法 target = TargetHolder.class。 */
    public static class TargetHolder implements IAriaObject {
        @AriaObjectConstructor("Holder")
        public TargetHolder(InvocationData data) {}
        @Override public String getTypeName() { return "Holder"; }
        @Override public String stringValue() { return "HOLDER_SELF"; }
    }

    static volatile String TARGET_OBS = null;

    @AriaNamespace("targetfns")
    public static class TargetFns {
        // target 对象函数: 接收者是 ObjectValue<TargetHolder>，方法用 data.getArgs()[0] 读第一个用户参数(mod 写法)
        @AriaInvokeHandler(value = "getNBT", target = TargetHolder.class)
        public static IValue<?> getNBT(InvocationData data) {
            IValue<?>[] a = data.getArgs();
            StringBuilder sb = new StringBuilder();
            sb.append("size=").append(data.size());
            sb.append(" getArgs()[0]=").append(a.length > 0 ? (a[0].getClass().getSimpleName() + ":" + a[0].stringValue()) : "MISSING");
            sb.append(" get(1)=").append(data.get(1) == null ? "null" : (data.get(1).getClass().getSimpleName() + ":" + data.get(1).stringValue()));
            sb.append(" target=").append(data.getTarget() == null ? "null" : data.getTarget().getClass().getSimpleName());
            TARGET_OBS = sb.toString();
            return NoneValue.NONE;
        }
    }

    @Test
    void t02_targetObjectFunctionArgShift() throws AriaException {
        Aria.getEngine().initialize();
        CallableManager.INSTANCE.registerObject(TargetHolder.class);
        TARGET_OBS = null;
        Context ctx = new Context(new GlobalStorage());
        Aria.eval("var.h = Holder()\nh.getNBT('myKey')\n", ctx);
        System.out.println("[2][target-fn] mod 用 getArgs()[0] 读第一个用户参数 'myKey' 时看到: " + TARGET_OBS);
        System.out.println("[2] 期望(Shimmer): getArgs()[0]='myKey'; 若 Aria self-prepend: getArgs()[0]=self(HOLDER_SELF), 真 key 在 get(1)");
    }

    // ============================================================
    // 3 & 4. ~ 文本转义: 双引号含反斜杠 / 花括号
    // ============================================================

    @Test
    void t03_backslashInDoubleQuote() {
        // mod ShimmerUtils.handleText 用双引号包 ~ 文本; 若含裸反斜杠, Aria 是否编译崩
        String[] samples = {
            "\"C:\\path\\to\"",          // Windows 路径
            "\"color \\c code\"",         // \c 颜色码残留
            "\"trailing \\\""             // 反斜杠在串尾(转义了引号) -> 实际测未闭合, 换个
        };
        for (String s : samples) {
            String code = s + "\n";
            try {
                IValue<?> r = Aria.eval(code, new Context(new GlobalStorage()));
                System.out.println("[3][backslash] compile OK for " + escape(code) + " -> " + r.stringValue());
            } catch (Throwable e) {
                System.out.println("[3][backslash] THROW for " + escape(code) + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    @Test
    void t04_bracesInDoubleQuote() {
        String[] samples = {
            "\"json {0}\"",               // 占位符
            "\"deco {abc}\"",             // 非法插值内容
            "\"open { only\"",            // 不配对
            "\"close } only\""            // 裸右括号
        };
        for (String s : samples) {
            String code = s + "\n";
            try {
                IValue<?> r = Aria.eval(code, new Context(new GlobalStorage()));
                System.out.println("[4][braces-dq] compile OK for " + escape(code) + " -> " + r.stringValue());
            } catch (Throwable e) {
                System.out.println("[4][braces-dq] THROW for " + escape(code) + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    @Test
    void t05_singleQuoteNoInterpolation() {
        String[] samples = {
            "'json {0}'",
            "'C:\\\\path'",   // 单引号里反斜杠(Aria 单引号仍走 readEscapeSequence, \\ 是合法转义)
        };
        for (String s : samples) {
            String code = s + "\n";
            try {
                IValue<?> r = Aria.eval(code, new Context(new GlobalStorage()));
                System.out.println("[5][single-quote] compile OK for " + escape(code) + " -> [" + r.stringValue() + "]");
            } catch (Throwable e) {
                System.out.println("[5][single-quote] THROW for " + escape(code) + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        // 裸反斜杠在单引号里(未知转义)是否也崩
        try {
            IValue<?> r = Aria.eval("'raw \\c code'\n", new Context(new GlobalStorage()));
            System.out.println("[5][single-quote] bare-backslash compile OK -> [" + r.stringValue() + "]");
        } catch (Throwable e) {
            System.out.println("[5][single-quote] bare-backslash THROW -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ============================================================
    // 6. StringValue.booleanValue: 任意非空即 true
    // ============================================================

    @Test
    void t06_stringBooleanValue() {
        System.out.println("[6][string-bool] \"false\".booleanValue()=" + new StringValue("false").booleanValue());
        System.out.println("[6][string-bool] \"0\".booleanValue()=" + new StringValue("0").booleanValue());
        System.out.println("[6][string-bool] \"true\".booleanValue()=" + new StringValue("true").booleanValue());
        System.out.println("[6][string-bool] \"\".booleanValue()=" + new StringValue("").booleanValue());
        System.out.println("[6] Shimmer: 仅 \"true\"->true; Aria: 任意非空->true");
    }

    // ============================================================
    // 7. NoneValue.stringValue -> "none"
    // ============================================================

    @Test
    void t07_noneStringValue() {
        System.out.println("[7][none-str] NoneValue.stringValue()=[" + NoneValue.NONE.stringValue() + "]");
        System.out.println("[7] Shimmer: \"\"; Aria: \"none\"");
        // 拼接场景
        try {
            IValue<?> r = Aria.eval("\"HP:\" + var.hp\n", new Context(new GlobalStorage()));
            System.out.println("[7][none-concat] \"HP:\" + (未设 hp) = [" + r.stringValue() + "]");
        } catch (Throwable e) {
            System.out.println("[7][none-concat] THROW: " + e.getMessage());
        }
    }

    // ============================================================
    // 12. number 真值判定: value != 0 (负数为 true)
    // ============================================================

    @Test
    void t12_numberBooleanValue() {
        System.out.println("[12][num-bool] (-5).booleanValue()=" + new NumberValue(-5).booleanValue());
        System.out.println("[12][num-bool] (0).booleanValue()=" + new NumberValue(0).booleanValue());
        System.out.println("[12][num-bool] (3).booleanValue()=" + new NumberValue(3).booleanValue());
        System.out.println("[12] Shimmer: value>0 (负数 false); Aria: value!=0 (负数 true)");
        // 在条件里验证
        try {
            IValue<?> r = Aria.eval("if (0 - 5) { 'yes' } else { 'no' }\n", new Context(new GlobalStorage()));
            System.out.println("[12][num-bool-if] if(-5) -> [" + r.stringValue() + "]  (Shimmer=no, Aria=yes)");
        } catch (Throwable e) {
            System.out.println("[12][num-bool-if] THROW: " + e.getMessage());
        }
    }

    // ============================================================
    // 13. && / || 短路与返回值
    // ============================================================

    @Test
    void t13_logicalShortCircuit() {
        Context ctx = new Context(new GlobalStorage());
        // 右侧带副作用: 若短路则不自增
        CallableManager.INSTANCE.registerStaticFunction("sc", "tick", data -> {
            SC_COUNTER++;
            return NumberValue.of(1);
        });
        SC_COUNTER = 0;
        try {
            Aria.eval("false && sc.tick()\n", ctx);
            System.out.println("[13][short-circuit] false && sc.tick(): tick 调用次数=" + SC_COUNTER + " (Shimmer=0 短路, Aria=1 无短路)");
        } catch (Throwable e) {
            System.out.println("[13][short-circuit] THROW: " + e.getMessage());
        }
        // 返回值类型: a && b 返回操作数还是布尔
        try {
            IValue<?> r = Aria.eval("5 && 7\n", new Context(new GlobalStorage()));
            System.out.println("[13][return-val] 5 && 7 -> class=" + r.getClass().getSimpleName() + " val=" + r.stringValue()
                    + " (Shimmer=BooleanValue true, Aria=NumberValue 7)");
        } catch (Throwable e) {
            System.out.println("[13][return-val] THROW: " + e.getMessage());
        }
    }
    static int SC_COUNTER = 0;

    // ============================================================
    // 14. 除零 / 取余零
    // ============================================================

    @Test
    void t14_divModByZero() {
        evalPrint("[14][div] 5 / 0", "5 / 0\n");
        evalPrint("[14][mod] 5 % 0", "5 % 0\n");
        evalPrint("[14][div-var] 5 / var.z (z=none/0?)", "var.z = 0\n5 / z\n");
        System.out.println("[14] Shimmer: 除零/取余零 -> 0; Aria: Infinity/NaN");
    }

    // ============================================================
    // 15. number -> string 格式 (整数去尾)
    // ============================================================

    @Test
    void t15_numberToString() {
        System.out.println("[15][num-str] NumberValue(5).stringValue()=[" + new NumberValue(5).stringValue() + "]");
        System.out.println("[15][num-str] NumberValue(5.5).stringValue()=[" + new NumberValue(5.5).stringValue() + "]");
        evalPrint("[15][concat] \"v=\" + 5", "\"v=\" + 5\n");
        System.out.println("[15] Shimmer: 5->\"5.0\"; Aria: 5->\"5\"");
    }

    // ============================================================
    // 16. none 减法语义
    // ============================================================

    @Test
    void t16_noneSubtraction() {
        evalPrint("[16][none-sub] none - 5", "var.n - 5\n");  // var.n 未设 = none
        System.out.println("[16] Shimmer: none - 5 -> 5(右操作数); Aria: none - 5 -> none");
    }

    // ============================================================
    // helpers
    // ============================================================

    private static void evalPrint(String label, String code) {
        try {
            IValue<?> r = Aria.eval(code, new Context(new GlobalStorage()));
            System.out.println(label + " -> class=" + r.getClass().getSimpleName() + " val=[" + r.stringValue() + "]");
        } catch (Throwable e) {
            System.out.println(label + " -> THROW " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }
}
