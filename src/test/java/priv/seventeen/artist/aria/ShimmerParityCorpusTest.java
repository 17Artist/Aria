/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package priv.seventeen.artist.aria;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import priv.seventeen.artist.aria.api.AriaCompilationUnit;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.GlobalStorage;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.value.IValue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A7：Shimmer↔Aria 对齐工程的差分语料永久回归(固化 A9-P2 双 jar 实测)。
 *
 * <p>语料与期望值来自 scratchpad/parity 的 A9-P2 复验记录(Shimmer-1.56.58 vs Aria 工作区
 * A1-A5+A9P2，REAL-MISALIGNMENT=0)，经 gen_fixtures.py 从 compare5_report.json 机械提取为
 * src/test/resources/parity/*.fixture(6 个语料文件，共 338 case)。执行方式与实测驱动器
 * (AriaRun5)完全一致：每语料文件一个共享 GlobalStorage、每 case 新 Context、
 * {@code Aria.compile(name, ctx, code, true)}(lenient，i-Common 生产配置)编译一次、
 * 同一 unit 连续执行 5 轮(JIT_THRESHOLD=1 → R1 解释执行，R2-R5 编译码)。</p>
 *
 * <p>分类与断言：</p>
 * <ul>
 *   <li><b>PASS</b>(250)：期望值 = P2 实测的 Shimmer 侧输出序列(与 Aria 侧逐轮相等，生成期机械校验)，
 *       逐轮断言 stringValue + 值类型；ERR 轮断言"抛异常"且 Aria 消息核心固定(防漂移，
 *       行号后缀与 gui-chain-9 包装前后缀归一后 contains 比对——两引擎消息文本本就不同，不逐字比 Shimmer)。</li>
 *   <li><b>EXPECTED</b>(57)：计划内自由区(多为 Shimmer 编译错而 Aria 可用)，固化断言 Aria 当前行为
 *       (期望值 = P2 实测 Aria 侧)，防无声漂移；fixture 内注有 Shimmer 侧行为。</li>
 *   <li><b>SKIP</b>(31)：CORPUS-ISSUE(30，A9P1 §5：非 Shimmer 合法书写形态/占位文本/非确定值)
 *       与计时性非确定(1，J4 async 竞态)。仍按原顺序执行以保持跨 case 全局状态一致，但不断言。</li>
 * </ul>
 *
 * <p>若本测试失败而语料未变：优先怀疑 Aria 行为漂移(对照 fixture 中记录的 P2 实测值定位)。</p>
 */
public class ShimmerParityCorpusTest {

    private static final String[] FILES = {
            "cases", "cases2", "cases3", "cases4", "cases_findings", "cases_findings2"
    };
    private static final int ROUNDS = 5;

    /** 归一化 "(line N:C)" / "(line N-M)" 后缀：解释轮带行号、JIT 轮不带(值级等价，消息核心不变)。 */
    private static final Pattern LINE_SUFFIX = Pattern.compile(" \\(line \\d+(?::\\d+|-\\d+)?\\)");

    @TestFactory
    Stream<DynamicNode> parityCorpus() {
        return Stream.of(FILES).map(this::corpusContainer);
    }

    private DynamicContainer corpusContainer(String file) {
        List<Fixture> fixtures;
        try {
            fixtures = loadFixtures(file);
        } catch (Exception e) {
            throw new IllegalStateException("载入 fixture 失败: " + file, e);
        }

        // 与 AriaRun5 一致：文件级共享 GlobalStorage，按 case 顺序全部执行(含 SKIP，保持全局状态一致)，
        // 先收集实际输出、再生成断言节点——保证执行顺序不受测试引擎调度影响。
        Interpreter.resetCallDepth();
        Interpreter.clearSandbox();
        GlobalStorage gs = new GlobalStorage();
        List<DynamicTest> tests = new ArrayList<>();
        for (Fixture fx : fixtures) {
            String[] actual = runCase(gs, fx.src);
            String display = file + "#" + fx.caseId + " [" + fx.cls + "]"
                    + (fx.finding != null ? " FINDING " + fx.finding : "");
            tests.add(DynamicTest.dynamicTest(display, () -> assertCase(fx, actual)));
        }
        return DynamicContainer.dynamicContainer(file + " (" + fixtures.size() + " cases)", tests.stream());
    }

    // ------------------------------------------------------------------
    // 执行(镜像 AriaRun5 驱动器)
    // ------------------------------------------------------------------

    /** 返回每轮实际输出："OK <value> ##<Type>" 或 "ERR <SimpleClassName>: <message(换行→' | ')>"。 */
    private String[] runCase(GlobalStorage gs, String src) {
        String code = src.isEmpty() ? "" : src + "\n"; // AriaRun5 逐行 append("\n")
        String[] out = new String[ROUNDS];
        Context ctx = new Context(gs);
        AriaCompilationUnit unit = null;
        String compileErr = null;
        try {
            unit = Aria.compile("t", ctx, code, true); // lenient = i-Common 生产配置
        } catch (Throwable e) {
            compileErr = "ERR " + e.getClass().getSimpleName() + ": " + normalizeMsg(e.getMessage());
        }
        for (int r = 0; r < ROUNDS; r++) {
            if (compileErr != null) {
                out[r] = compileErr;
                continue;
            }
            try {
                IValue<?> v = unit.execute();
                out[r] = "OK " + fmt(v);
            } catch (Throwable e) {
                out[r] = "ERR " + e.getClass().getSimpleName() + ": " + normalizeMsg(e.getMessage());
            }
        }
        return out;
    }

    /** 与 AriaRun5.fmt 一致：stringValue(\r 剥离、\n→字面 \\n) + " ##" + 值类型简名。 */
    private static String fmt(IValue<?> v) {
        if (v == null) return "null ##null";
        String s;
        try {
            s = String.valueOf(v.stringValue());
        } catch (Throwable t) {
            s = v.toString();
        }
        return s.replace("\r", "").replace("\n", "\\n") + " ##" + v.getClass().getSimpleName();
    }

    private static String normalizeMsg(String msg) {
        return String.valueOf(msg).replace("\r", "").replace("\n", " | ");
    }

    // ------------------------------------------------------------------
    // 断言
    // ------------------------------------------------------------------

    private void assertCase(Fixture fx, String[] actual) {
        assumeTrue(!"SKIP".equals(fx.cls), fx.skipReason != null ? fx.skipReason : "SKIP");
        assertEquals(ROUNDS, fx.rounds.size(), "fixture 轮数异常: " + fx.caseId);
        for (int r = 0; r < ROUNDS; r++) {
            Round exp = fx.rounds.get(r);
            String act = actual[r];
            String where = "R" + (r + 1) + " (" + fx.cls + ")"
                    + "\n  SRC   : " + fx.src.replace("\n", " \\n ")
                    + "\n  期望  : " + exp.raw
                    + "\n  实际  : " + act
                    + "\n  (期望值为 A9-P2 双 jar 实测记录；失败=Aria 行为相对 P2 漂移)";
            if (exp.ok) {
                assertTrue(act.startsWith("OK "), "应正常返回值，实际抛了异常 " + where);
                String actVal = act.substring(3);
                int i = actVal.lastIndexOf(" ##");
                String actType = i >= 0 ? actVal.substring(i + 3) : "";
                if (i >= 0) actVal = actVal.substring(0, i);
                assertEquals(exp.value, actVal, "stringValue 漂移 " + where);
                assertEquals(exp.type, actType, "值类型漂移 " + where);
            } else {
                assertTrue(act.startsWith("ERR "), "应抛异常(ERR)，实际正常返回 " + where);
                String rest = act.substring(4);
                int i = rest.indexOf(": ");
                String actCls = i >= 0 ? rest.substring(0, i) : rest;
                String actMsg = i >= 0 ? rest.substring(i + 2) : "";
                assertEquals(exp.errClass, actCls, "异常类型漂移 " + where);
                // Aria 自身消息核心固定(防漂移)：归一化行号后缀后，实际消息须包含 P2 记录的消息核心。
                // (gui-chain-9 后消息带 "单元: [t] ... 错误信息: <核心>" 包装，故用 contains。)
                String expCore = stripLineSuffix(exp.errMsg);
                String actNorm = stripLineSuffix(actMsg);
                assertTrue(actNorm.contains(expCore),
                        "异常消息核心漂移，应含 [" + expCore + "] " + where);
            }
        }
    }

    private static String stripLineSuffix(String s) {
        Matcher m = LINE_SUFFIX.matcher(s);
        return m.replaceAll("");
    }

    // ------------------------------------------------------------------
    // fixture 解析
    // ------------------------------------------------------------------

    private static final class Round {
        final boolean ok;
        final String value;    // ok=true
        final String type;     // ok=true
        final String errClass; // ok=false
        final String errMsg;   // ok=false (P2 实测 Aria 消息核心)
        final String raw;

        Round(boolean ok, String value, String type, String errClass, String errMsg, String raw) {
            this.ok = ok;
            this.value = value;
            this.type = type;
            this.errClass = errClass;
            this.errMsg = errMsg;
            this.raw = raw;
        }
    }

    private static final class Fixture {
        int caseId;
        String cls;      // PASS / EXPECTED / SKIP
        String finding;  // 语料 // FINDING id(可空)
        String skipReason;
        String src = "";
        final List<Round> rounds = new ArrayList<>();
    }

    private static final Pattern CASE_HDR = Pattern.compile("^### CASE (\\d+) \\| (\\w+)(?: \\| FINDING (\\S+))?$");
    private static final Pattern ROUND_LINE = Pattern.compile("^R(\\d+) (OK|ERR) (.*)$", Pattern.DOTALL);

    private List<Fixture> loadFixtures(String file) throws Exception {
        String path = "/parity/" + file + ".fixture";
        InputStream in = getClass().getResourceAsStream(path);
        assertNotNull(in, "fixture 资源缺失: " + path);
        List<Fixture> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Fixture cur = null;
            List<String> srcLines = null;
            boolean inSrc = false, inExpect = false;
            String line;
            while ((line = br.readLine()) != null) {
                Matcher hdr = CASE_HDR.matcher(line);
                if (hdr.matches()) {
                    cur = new Fixture();
                    cur.caseId = Integer.parseInt(hdr.group(1));
                    cur.cls = hdr.group(2);
                    cur.finding = hdr.group(3);
                    srcLines = new ArrayList<>();
                    inSrc = inExpect = false;
                    result.add(cur);
                    continue;
                }
                if (cur == null) continue; // 文件头注释
                if (line.equals("--- SRC")) { inSrc = true; inExpect = false; continue; }
                if (line.equals("--- EXPECT")) { inSrc = false; inExpect = true; continue; }
                if (line.equals("--- END")) {
                    cur.src = String.join("\n", srcLines);
                    cur = null;
                    inSrc = inExpect = false;
                    continue;
                }
                if (inSrc) {
                    srcLines.add(line);
                } else if (inExpect) {
                    Matcher rm = ROUND_LINE.matcher(line);
                    if (!rm.matches()) fail("fixture 轮行格式异常: " + line);
                    String rest = rm.group(3);
                    if ("OK".equals(rm.group(2))) {
                        int i = rest.lastIndexOf(" ##");
                        String val = i >= 0 ? rest.substring(0, i) : rest;
                        String ty = i >= 0 ? rest.substring(i + 3) : "";
                        cur.rounds.add(new Round(true, val, ty, null, null, line));
                    } else {
                        int i = rest.indexOf(": ");
                        String cls = i >= 0 ? rest.substring(0, i) : rest;
                        String msg = i >= 0 ? rest.substring(i + 2) : "";
                        cur.rounds.add(new Round(false, null, null, cls, msg, line));
                    }
                } else if (line.startsWith("# ") && "SKIP".equals(cur.cls) && cur.skipReason == null) {
                    cur.skipReason = line.substring(2);
                }
            }
        }
        return result;
    }
}
