# Migrating scripts toward BeanShell 3.x

This guide describes changes that can affect existing scripts and ways to make
their intent explicit. It separates behavior already present on the development
branch from pending fixes. Build and Java integration notices are in the
[changelog](CHANGES.md).

## Versions and pending changes

The examples were checked on 8 September 2026. Historical comparisons use the
published artifacts **`org.beanshell:bsh:2.0b4`** and
**`org.apache-extras.beanshell:bsh:2.0b6`**. The development baseline is upstream
master [**`eee36c81`**](https://github.com/beanshell/beanshell/tree/eee36c81c35525fd771285e77b6fb8173db3f1dc).
These are specific versions, not a claim that every 2.x release behaves alike.

The following PRs were still open at that check. Sections marked **pending**
describe their respective heads, not behavior already available on master or in
a released 3.x artifact. The PRs were checked separately; this guide does not
claim that their combined integration has been validated.

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
the enclosing scope. That had already changed by 2.0b6: the new variable belongs
to the block. Master retains the latter behavior.

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

Alternatively, an explicit parent assignment works for this ordinary block:

```java
{ super.x = 5; }
x; // 5
```

Prefer enclosing-scope initialization when migrating code with nested blocks or
methods; it makes the intended lifetime visible. A variable going out of scope
is different from a cache defect that loses a variable while it is still in
scope.

[#555](https://github.com/beanshell/beanshell/issues/555) records the scoping change
and workarounds. [#727](https://github.com/beanshell/beanshell/issues/727) requests
an optional mode restoring the older behavior, but its implementation in
[#728](https://github.com/beanshell/beanshell/pull/728) was closed without merging.
The proposed `setBsh2ScopingCompatibility` setting is not an available API in the
checked master. The existing `setCompatibility()` / `bsh.compatibility` setting
concerns loading classes from Java source; it does not restore old block scope.

## Undefined values in expressions

Both 2.0b4 and 2.0b6 turn an undefined value into the text `void` in this string
expression. Master reports an evaluation error:

```java
"hello " + missing;
```

Initialize the variable before using it, or handle absence explicitly. Initialize
the result in the enclosing scope if it is needed after the block:

```java
message = null;
if (missing == void) {
    message = "hello guest";
} else {
    message = "hello " + missing;
}
message; // "hello guest" when missing is undefined
```

`missing == void` remains a supported existence check on master. An undefined
value and an explicitly assigned `null` are distinct; initializing a variable to
`null` does not make it undefined. Avoid replacing every `void` check with a null
check.

[#746](https://github.com/beanshell/beanshell/issues/746) asks for these differences
to be documented. It does not require restoring string concatenation with an
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
primitive values, but it is not a method common to arbitrary Java objects.

On master, `i instanceof Number` can replace `isNumber()` for numeric values;
characters are not instances of `Number`. To obtain a `Number` instead of using
the old `numberValue()` helper, assign or cast the value explicitly:

```java
int i = 1;
Number value = i;
value.intValue(); // 1
```

These replacements target master: the checked 2.0b4 and 2.0b6 releases return
false for `int i = 1; i instanceof Number` and reject the assignment above.

The [Primitive 3.0 discussion (#687)](https://github.com/beanshell/beanshell/issues/687)
contains the original compatibility analysis and details of numeric convenience
methods. These script-facing changes do not mean that the Java implementation
has stopped using `bsh.Primitive` internally.

## Java helpers used below

The varargs changes concern calls to **compiled Java methods and constructors**.
To try the examples below, compile this file as `MigrationExamples.java` and put
the resulting classes on the application's classpath. The same helper also
provides methods for observing overload selection, reference identity, and
property access without depending on their return-value formatting.

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

For example, `javac -d . MigrationExamples.java` creates the `examples` package
under the current directory. Add that directory to the Java classpath when
launching BeanShell. Import the helper in the scripts that use it:

```java
import examples.MigrationExamples;
```

## Null arguments and Java varargs

**Pending #797.** A Java `Object...` parameter is an `Object[]`. Passing a null
array, passing one null element, and supplying no elements are distinct calls.
Master incorrectly packs a single null argument into a one-element array. The
fix uses the argument's available declared type to preserve that distinction.

For the Java helpers above:

| Call | Master | With #797 |
| --- | --- | --- |
| `MigrationExamples.arguments(null)` | `[null]` | `null array` |
| `MigrationExamples.arguments((Object[]) null)` | `[null]` | `null array` |
| `MigrationExamples.arguments((Object) null)` | `[null]` | `[null]` |
| `MigrationExamples.arguments()` | `[]` | `[]` |
| `MigrationExamples.arguments(new Object[] {null})` | `[null]` | `[null]` |
| `MigrationExamples.integers(null)` | `[0]` | `null array` |

To request one null element for `Object...`, use `(Object) null` or an explicit
`new Object[] {null}`. To request an empty array, omit the varargs arguments or
pass an empty array. For `int...`, use an explicit `new int[] {0}` when one zero
element is intended; a null array is not a zero element.

The distinction also applies to null values obtained from typed variables:

```java
Object[] array = null;
Object element = null;
MigrationExamples.arguments(array);   // "null array" with #797
MigrationExamples.arguments(element); // "[null]" with #797
```

Available declared null types from casts, fields, array elements, and method
results are also retained. This affects overload selection: given the helper's
`pick(Object)` and `pick(Object...)` methods, bare null selects the more-specific
array parameter with #797. Use an explicitly object-typed null to select the
scalar overload.

| Call | Master | With #797 |
| --- | --- | --- |
| `MigrationExamples.pick(null)` | `Object` | `Object[]` |
| `MigrationExamples.pick((Object[]) null)` | `Object` | `Object[]` |
| `MigrationExamples.pick((Object) null)` | `Object` | `Object` |

Constructor calls follow the same rule: `new MigrationExamples(null).values` is
a null array with #797, whereas
`new MigrationExamples((Object) null).values` contains one null element.

This fix does not switch all dispatch to Java's declared-type rules. Non-null
arguments retain runtime-type dispatch, including non-null arrays stored in an
`Object` variable. BeanShell's existing String preference for ambiguous bare-null
calls remains. Expressions without an available declared null type retain their
untyped-null behavior. Script-defined varargs packing and generated constructor
delegation are outside this change.

## Float arithmetic and numeric overloads

**Pending #796.** Float arithmetic follows Java promotion and rounding rules.
For the ordinary arithmetic operators `+`, `-`, `*`, `/`, and `%`, a float combined
with a byte, short, char, int, long, or another float is evaluated as float. A
double operand makes the operation double. This revises the earlier double-first
calculation and float-overflow widening discussed in
[#71](https://github.com/beanshell/beanshell/issues/71).

| Expression | Master | With #796 |
| --- | --- | --- |
| `(1f * 2f).getClass()` | `double.class` | `float.class` |
| `16777216f + 1` | `16777217.0` | `16777216.0` |
| `Float.MAX_VALUE * 2f` | Finite double | Float positive infinity |
| `1f + 2d` | Double `3.0` | Double `3.0` |

Promotion happens **before** the calculation. Assigning the result to a double
afterward does not recover precision already lost during a float operation.
Widen an operand when a double calculation is intended:

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

Compound assignments also use the promoted operand types before converting the
result back to the variable's type. For example, `floatValue /= integerValue`
rounds as a float operation, and `longValue += floatValue` converts the long to
float before adding, then converts the result back to long. That can lose integer
precision. Choose double operands or an explicit big-number calculation when
the required precision or range exceeds float.

Float results retain Java's signed zero, NaN, infinity, and gradual-underflow
behavior. A float overflow no longer triggers a wider finite result. Existing
double arithmetic, explicit `BigInteger`/`BigDecimal` promotion, and the `**`
power extension retain their behavior; do not infer their rules from the float
table alone. See [#796](https://github.com/beanshell/beanshell/pull/796) for the
Java comparisons and the relationship to #767/#768.

## Scalar and array overloads

**Pending #793.** Method applicability distinguishes complete types, including
array rank. A scalar string must not be accepted as a `String[]` merely because
the ultimate element type is `String`. Overload declaration order must not cause
the scalar call below to enter the array method:

```java
pick(value) { return value; }
pick(String[] values) { return pick(values[1]); }
pick("a"); // "a" with #793
```

Pass `new String[] {"a", "b"}` when the array overload is intended. Overloads
accepting `String[]` and `String[][]` likewise receive one- and two-dimensional
arrays, respectively. Code depending on accidental scalar-to-array selection
should construct the intended array explicitly.

Parameter brackets after the name count too. `String values[]` means `String[]`,
and `String[] values[]` means `String[][]`:

```java
first(String[] values[]) { return values[0][0]; }
first(new String[][] {{"a"}}); // "a" with #793
```

These spellings already parse on master, and the isolated call above can already
succeed. The fix applies the combined dimensions consistently to overload
selection and generated Java signatures. Rewriting the parameters as
`String[] values` or `String[][] values` can make the dimensionality clearer.
The Java reflection impact is also noted in the changelog.

## Method calls and property aliases

**Pending #798.** An applicable real method is selected before a JavaBean
property alias of the same name. On master, the two share an overload list, so
reflection order can make a call such as `up()` invoke `isUp()` instead. An
inherited cache can also retain the accessor in place of the real method.

With the Java `Switch` helper above:

```java
device = new MigrationExamples.Switch();
device.up();
device.isUp(); // true with #798: the real up() changed the state
```

Use `device.isUp()` when the getter is intended. Property reads and writes retain
their existing accessors and field precedence; `device.up` can still read the
property, whereas `device.up()` requests a method call.

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
imports likewise prefer actual static method names while retaining alias-only
fallback. [#798](https://github.com/beanshell/beanshell/pull/798) preserves property
naming and does not introduce a general redesign of JavaBean setter selection.

## Workarounds that can be removed

The following pending fixes restore operations that fail on the checked master.
They generally do not require changes to scripts that already work.

- **Numeric reference casts (#792):** `(Number) Double.valueOf(1)` can throw a
  `ClassCastException` on master. With the fix, casts and assignments to an
  assignable numeric reference type preserve the original object. Reboxing or
  converting through another number type solely to avoid that failure is no
  longer necessary. This concerns reference casts, not narrowing primitive
  conversions. [#792](https://github.com/beanshell/beanshell/pull/792)
- **Boxed unary operators (#795):** numeric and character wrappers support the
  applicable unary operators without manual unboxing. Increment/decrement
  preserve boxed stored values and prefix results; postfix returns the original
  boxed value. Other unary results follow primitive promotion. Raw
  `BigInteger`/`BigDecimal` values also reach their existing arithmetic support.
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
