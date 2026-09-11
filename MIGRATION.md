# Migrating scripts toward BeanShell 3.x

This guide describes changes that can affect existing scripts and ways to make
their intent explicit. It separates behavior already present on the development
branch from pending fixes. Build and Java integration notices are in the
[changelog](CHANGES.md).

## Versions and pending changes

We checked the examples on 8 September 2026. Historical comparisons use the
published artifacts **`org.beanshell:bsh:2.0b4`** and
**`org.apache-extras.beanshell:bsh:2.0b6`**. The development baseline is upstream
master [**`eee36c81`**](https://github.com/beanshell/beanshell/tree/eee36c81c35525fd771285e77b6fb8173db3f1dc).
These are specific versions, not a claim that every 2.x release behaves alike.

The following pull requests (PRs) were still open at that check. The examples in
each **pending** section use the PR's checked head. Those changes are not on
master or in a released 3.x artifact. We checked each PR separately. We did not
test them in a combined build.

| Change | PR | Checked head |
| --- | --- | --- |
| Numeric reference casts | [#792](https://github.com/beanshell/beanshell/pull/792) | `8653b3ab` |
| Scalar/array overloads and parameter dimensions | [#793](https://github.com/beanshell/beanshell/pull/793) | `3a91f2b3` |
| Boxed unary operators | [#795](https://github.com/beanshell/beanshell/pull/795) | `f9db06b8` |
| Float arithmetic | [#796](https://github.com/beanshell/beanshell/pull/796) | `1deca8c9` |
| Java null arguments and varargs | [#797](https://github.com/beanshell/beanshell/pull/797) | `d58d15e8` |
| Methods and property aliases | [#798](https://github.com/beanshell/beanshell/pull/798) | `98f0f623` |

## Variables created inside blocks

In 2.0b4, a new untyped variable assigned inside an ordinary block is visible in
the enclosing scope. In 2.0b6, the new variable belongs to the block. Master uses
this behavior.

```java
{ x = 5; }
x == void; // false in 2.0b4; true in 2.0b6 and master
```

Initialize the variable in the scope where you need to use it. Assignment in a
child block then updates the existing variable:

```java
x = null;
{ x = 5; }
x; // 5 in all three checked versions
```

You can also assign the variable through `super` for this ordinary block:

```java
{ super.x = 5; }
x; // 5
```

For scripts with nested blocks or methods, initialize the variable in the
enclosing scope. This makes the variable's intended lifetime clear. A variable
can go out of scope by design. A cache defect can lose a variable that is still
in scope.

[#555](https://github.com/beanshell/beanshell/issues/555) records the scoping change
and workarounds. [#727](https://github.com/beanshell/beanshell/issues/727) asks for
an optional mode that restores the earlier behavior. Its implementation in
[#728](https://github.com/beanshell/beanshell/pull/728) is closed and unmerged.
The checked master has no `setBsh2ScopingCompatibility` API. The existing
`setCompatibility()` / `bsh.compatibility` setting controls Java source loading.
It does not restore the block scope from 2.0b4.

## Undefined values in expressions

Both 2.0b4 and 2.0b6 turn an undefined value into the text `void` in this string
expression. Master reports an evaluation error:

```java
"hello " + missing;
```

Before you use a variable, initialize it or check whether it exists. If you need
the result after the block, initialize it in the enclosing scope:

```java
message = null;
if (missing == void) {
    message = "hello guest";
} else {
    message = "hello " + missing;
}
message; // "hello guest" when missing is undefined
```

`missing == void` remains a supported existence check on master. A variable with
an assigned `null` value is different from an undefined variable. If you
initialize a variable to `null`, it is still defined. Do not replace every
`void` check with a null check.

[#746](https://github.com/beanshell/beanshell/issues/746) asks for documentation of
these differences. It does not ask for a change to string concatenation with an
undefined value.

## Primitive values and wrapper utility methods

Older scripts could observe the internal `bsh.Primitive` wrapper. On master,
script-visible primitive operations expose the value and its primitive type.
For a fresh `int i = 1`, the checked versions behave as follows:

| Expression | 2.0b4 and 2.0b6 | Master |
| --- | --- | --- |
| `i.getClass()` | `bsh.Primitive.class` | `int.class` |
| `i.getType()` | `int.class` | `int.class` |
| `i.getValue()` | `1` | Method-not-found error |

Use `i` directly instead of `i.getValue()`. To ask whether its exposed type is
primitive, use `i.getClass().isPrimitive()`. `getType()` remains available on
primitive values. Java objects do not all have a `getType()` method.

On master, `i instanceof Number` can replace `isNumber()` for numeric values.
Characters are not instances of `Number`. To get a `Number`, assign or cast the
value to that type instead of the `numberValue()` call:

```java
int i = 1;
Number value = i;
value.intValue(); // 1
```

These replacements apply to master. The checked 2.0b4 and 2.0b6 releases return
false for `int i = 1; i instanceof Number`. Both releases reject the assignment
above.

The [Primitive 3.0 discussion (#687)](https://github.com/beanshell/beanshell/issues/687)
contains the original compatibility analysis and details of numeric convenience
methods. The Java implementation still uses `bsh.Primitive` internally.

## Java helpers used below

The varargs changes apply to calls to **compiled Java methods and constructors**.
With this helper, you can check overload selection, reference identity, and
property access. Save the following code in `MigrationExamples.java`:

```java
package examples;

import java.util.Arrays;

public class MigrationExamples {
    public final Object[] values;
    public MigrationExamples(Object... values) { this.values = values; }
    public static String arguments(Object... values) {
        return values == null ? "null array" : Arrays.toString(values);
    }
    public static String integers(int... values) {
        return values == null ? "null array" : Arrays.toString(values);
    }
    public static String pick(Object value) { return "Object"; }
    public static String pick(Object... values) { return "Object[]"; }
    public static boolean same(Object left, Object right) { return left == right; }

    public static class Switch {
        private boolean raised;
        public void up() { raised = true; }
        public boolean isUp() { return raised; }
    }
    public static class GetterOnly {
        public boolean isUp() { return true; }
    }
    public static class Label {
        public String title(Object value) { return "method"; }
        public void setTitle(String value) {}
    }
}
```

The compiler creates the `examples` package under the current directory. Use
these steps to make the helper available:

1. Compile the file with `javac -d . MigrationExamples.java`.
2. Before you start BeanShell, add the current directory to the Java classpath.

Import the helper in the scripts that use it:

```java
import examples.MigrationExamples;
```

## Null arguments and Java varargs

**Pending #797.** A Java `Object...` parameter is an `Object[]`. A null array, an
array with one null element, and an empty array are different values.
Master incorrectly packs a single null argument into a one-element array. The
fix uses the declared argument type, if available, to select the correct form.

For the Java helpers above:

| Call | Master | With #797 |
| --- | --- | --- |
| `MigrationExamples.arguments(null)` | `[null]` | `null array` |
| `MigrationExamples.arguments((Object[]) null)` | `[null]` | `null array` |
| `MigrationExamples.arguments((Object) null)` | `[null]` | `[null]` |
| `MigrationExamples.arguments()` | `[]` | `[]` |
| `MigrationExamples.arguments(new Object[] {null})` | `[null]` | `[null]` |
| `MigrationExamples.integers(null)` | `[0]` | `null array` |

To pass one null element for `Object...`, use `(Object) null` or an explicit
`new Object[] {null}`. To pass an empty array, call the method with no varargs
arguments or use an empty array. If you need one zero element for `int...`, use
`new int[] {0}`. A null array is not a zero element.

These rules also apply to null values from typed variables:

```java
Object[] array = null;
Object element = null;
MigrationExamples.arguments(array);   // "null array" with #797
MigrationExamples.arguments(element); // "[null]" with #797
```

BeanShell also keeps the declared type of null arguments from casts, fields,
array elements, and method results. This affects overload selection. With #797,
bare null selects `pick(Object...)` instead of `pick(Object)` because the array
parameter is more specific. Use `(Object) null` to select the scalar overload.

| Call | Master | With #797 |
| --- | --- | --- |
| `MigrationExamples.pick(null)` | `Object` | `Object[]` |
| `MigrationExamples.pick((Object[]) null)` | `Object` | `Object[]` |
| `MigrationExamples.pick((Object) null)` | `Object` | `Object` |

Constructor calls follow the same rule. With #797,
`new MigrationExamples(null).values` is a null array. But
`new MigrationExamples((Object) null).values` contains one null element.

This fix does not switch all dispatch to Java's declared-type rules. BeanShell
still uses runtime types for non-null arguments. This also applies to non-null
arrays in `Object` variables. BeanShell still prefers String for ambiguous
bare-null calls. If an expression gives no declared type for null, BeanShell
keeps its untyped-null behavior. This change does not include script-defined
varargs packing or generated constructor delegation.

## Float arithmetic and numeric overloads

**Pending #796.** Float arithmetic follows Java promotion and rounding rules.
For Java numeric primitives, these rules apply to `+`, `-`, `*`, `/`, and `%`.
With a `float` operand, BeanShell uses `float` arithmetic for byte, short, char,
int, long, and float operands. With a `double` operand, BeanShell uses `double`
arithmetic. These rules change the earlier double-first calculation and
float-overflow widening from
[#71](https://github.com/beanshell/beanshell/issues/71).

| Expression | Master | With #796 |
| --- | --- | --- |
| `(1f * 2f).getClass()` | `double.class` | `float.class` |
| `16777216f + 1` | `16777217.0` | `16777216.0` |
| `Float.MAX_VALUE * 2f` | Finite double | Float positive infinity |
| `1f + 2d` | Double `3.0` | Double `3.0` |

Promotion happens **before** the calculation. If you assign the result to a
double afterward, you cannot recover precision lost during the float operation.
If you need a double calculation, widen an operand before the calculation:

```java
float value = 16777216f;
double rounded = value + 1;        // 16777216.0 with #796
double widened = (double)value + 1; // 16777217.0
```

Result types can select a different overload:

```java
choose(float value) { return "float"; }
choose(double value) { return "double"; }
choose(1f * 2f); // "double" on master; "float" with #796
```

Compound assignments first calculate with the promoted operand types. They then
convert the result back to the variable's type. For example,
`floatValue /= integerValue` rounds as a float operation.

With `longValue += floatValue`, BeanShell converts the long to float before the
addition. It then converts the result back to long. That can lose integer
precision. If you need more precision or range than float gives, use double
operands or explicit big-number arithmetic.

Float results keep Java's signed zero, NaN, infinity, and gradual-underflow
behavior. A float overflow no longer triggers a wider finite result. The fix
does not change double arithmetic, explicit `BigInteger`/`BigDecimal` promotion,
or the `**` power extension. The float table does not specify their rules.
See [#796](https://github.com/beanshell/beanshell/pull/796) for the
Java comparisons and the relationship to #767/#768.

## Scalar and array overloads

**Pending #793.** Method applicability distinguishes complete types. The complete
type includes array rank. A scalar string is not a `String[]`. BeanShell must
use the complete types to select an overload. Overload declaration order must
not cause the scalar call below to enter the array method:

```java
pick(value) { return value; }
pick(String[] values) { return pick(values[1]); }
pick("a"); // "a" with #793
```

If you need the array overload, pass `new String[] {"a", "b"}`. An overload with a
`String[]` parameter receives a one-dimensional array. An overload with a
`String[][]` parameter receives a two-dimensional array. If your code uses
accidental scalar-to-array selection, construct the intended array explicitly.

Parameter brackets after the name count too. `String values[]` means `String[]`,
and `String[] values[]` means `String[][]`:

```java
first(String[] values[]) { return values[0][0]; }
first(new String[][] {{"a"}}); // "a" with #793
```

Master already parses these forms. The isolated call above can succeed on
master. The fix applies the combined dimensions consistently to overload
selection and generated Java signatures. You can write the parameters as
`String[] values` or `String[][] values` to make the dimensions clearer.
The changelog explains the effect on Java reflection.

## Method calls and property aliases

**Pending #798.** BeanShell selects an applicable real method before a JavaBean
property alias of the same name. On master, the two share an overload list, so
reflection order can make a call such as `up()` invoke `isUp()` instead. An
inherited cache can also keep the accessor in place of the real method.

With the Java `Switch` helper above:

```java
device = new MigrationExamples.Switch();
device.up();
device.isUp(); // true with #798: the real up() changed the state
```

To call the getter, use `device.isUp()`. Property reads and writes keep their
existing accessors and field precedence. `device.up` can still read the property.
`device.up()` makes a method call.

Real-method precedence applies even if the alias has a more-specific parameter:

```java
label = new MigrationExamples.Label();
label.title("x"); // "method" with #798; selects title(Object), not setTitle(String)
```

To invoke the setter explicitly, call `label.setTitle("x")`. If no real method
with that name is applicable, the property alias remains available:

```java
getter = new MigrationExamples.GetterOnly();
getter.up(); // true; falls back to isUp()
```

An exception from a selected real method does not trigger alias fallback. Static
imports also give priority to static methods with the requested name. They use a
property alias only if no applicable real method exists.
[#798](https://github.com/beanshell/beanshell/pull/798) keeps property naming rules.
It does not introduce a general redesign of JavaBean setter selection.

## Workarounds you can remove

These pending fixes correct operations that fail on the checked master.
Scripts that already work usually need no changes for these fixes.

- **Numeric reference casts (#792):** `(Number) Double.valueOf(1)` can throw a
  `ClassCastException` on master. With the fix, an assignable reference cast or
  assignment keeps the original numeric object. You no longer need to rebox or
  convert through another number type only to prevent that failure. This fix
  applies to reference casts. It does not change narrowing primitive conversions.
  [#792](https://github.com/beanshell/beanshell/pull/792)
- **Boxed unary operators (#795):** numeric and character wrappers support the
  applicable unary operators without manual unboxing. Increment and decrement
  keep stored values and prefix results boxed. Postfix returns the original
  boxed value. Other unary results follow primitive promotion. BeanShell also
  applies its existing arithmetic support to raw `BigInteger` and `BigDecimal`
  values.
  Unsupported operator/type combinations still fail.
  [#795](https://github.com/beanshell/beanshell/pull/795)

```java
Double original = Double.valueOf(1);
Number reference = (Number) original;
MigrationExamples.same(original, reference); // true with #792
```

```java
Integer boxed = Integer.valueOf(5);
Integer before = boxed++;
before; // 5 with #795; boxed now holds Integer 6
```

This guide covers the linked changes, not every difference between historical
BeanShell releases. Test representative application scripts against the exact
candidate version, especially where results choose overloads or values cross
Java/BeanShell boundaries.
