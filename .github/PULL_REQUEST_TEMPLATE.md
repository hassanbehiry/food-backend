# 🚀 Pull Request

## 🏷️ PR Title

Use the following convention:

```text
[<Feature / Scope> <Emoji>] - <Short Description>
```

Example:

```text
[Bottom Sheet ✨] - Add unified bottom sheet value API
```

---

## 📝 Summary

<!-- Briefly explain what this PR does and why it is needed. -->

This PR implements **<feature / fix / refactor name>** to address **<problem or requirement>**.

### 🎯 Goal

* <Primary goal>
* <Secondary goal, if applicable>
* <Important behavior or outcome>

---

## 🔨 Changes

<!-- List the important implementation changes introduced by this PR. -->

* <Change 1>
* <Change 2>
* <Change 3>
* <Change 4>

### ➕ Added

* <New components / APIs / behaviors>

### ♻️ Updated

* <Existing components / APIs / behaviors>

### 🗑️ Removed / Deprecated

* <Removed or deprecated functionality>
* `N/A` if nothing was removed or deprecated.

---

## 🧩 Technical Details

* 🏗️ **Architecture:** <architecture impact or N/A>
* 🔌 **API Changes:** <API impact or N/A>
* 🔄 **State Management:** <state changes or N/A>
* ⚡ **Performance:** <performance considerations or N/A>
* 🛡️ **Backward Compatibility:** <compatible / migration required / N/A>

---

## 🎨 UI / UX Changes

<!-- Remove this section if the PR has no visual impact. -->

### ⏪ Before

<Description, screenshot, GIF, or video>

### ⏩ After

<Description, screenshot, GIF, or video>

### ✅ States Verified

* [ ] 🏠 Default
* [ ] ⏳ Loading
* [ ] ✅ Success
* [ ] 📭 Empty
* [ ] ❌ Error
* [ ] 🚫 Disabled
* [ ] 🌍 RTL
* [ ] 🌐 LTR
* [ ] ☀️ Light mode
* [ ] 🌙 Dark mode
* [ ] 📱 Different screen sizes

---

## 🧪 How to Test

### 🛠️ Preconditions

1. <Required setup>
2. <Required account/data/configuration>

### 👣 Test Steps

1. <Step 1>
2. <Step 2>
3. <Step 3>
4. <Expected result>

### 🎯 Expected Result

<Clearly describe what the reviewer should observe after completing the test steps.>

---

## ✅ Validation

* [ ] 🏗️ Project builds successfully
* [ ] 🧪 Relevant tests pass
* [ ] 🤖 CI checks pass
* [ ] ⚠️ No new compiler warnings introduced
* [ ] 🧹 No unrelated code was modified
* [ ] 🔒 Existing behavior remains unchanged where required
* [ ] 👀 New behavior was manually verified
* [ ] 🧩 Edge cases were verified
* [ ] 🌍 RTL/LTR verified when applicable
* [ ] 🌓 Light/Dark mode verified when applicable
* [ ] ♿ Accessibility checked when applicable
* [ ] ⚡ Performance impact considered

---

## 🧪 Tests

### 🤖 Automated Tests

* <Test added/updated>
* `N/A` if no automated tests apply.

### 👤 Manual Tests

* <Scenario 1>
* <Scenario 2>
* <Scenario 3>

---

## ⚠️ Regression Risk

**Risk Level:** `<🟢 Low | 🟡 Medium | 🔴 High>`

### 💥 Possible Impact

* <Potential affected area>
* <Potential regression>

### 🛡️ Mitigation

* <How the implementation reduces or validates the risk>

---

## 🔗 Dependencies

* ⬅️ Depends on: <PR / task / N/A>
* ➡️ Blocks: <PR / task / N/A>
* 🔗 Related PRs: <PR / N/A>

---

## 🚨 Breaking Changes

* [ ] ✅ No breaking changes
* [ ] ⚠️ Contains breaking changes

If breaking changes exist:

```text
Describe:
1. 💥 What changed
2. 🎯 What is affected
3. 🔄 Required migration
4. 🛡️ Backward-compatibility strategy
```

---

## 🖼️ Screenshots / Recordings

| ⏪ Before | ⏩ After |
| -------- | ------- |
| <Before> | <After> |

---

## 👀 Reviewer Notes

Please pay particular attention to:

* 🔍 <Important implementation area>
* ⚠️ <Potential edge case>
* 🏗️ <Architecture/API decision>

---

## 📋 Final Checklist

* [ ] 🎯 Implementation matches the task requirements
* [ ] 📐 Code follows repository conventions
* [ ] 🧹 Code is clean and maintainable
* [ ] 🚫 No unnecessary refactoring was introduced
* [ ] 📁 No unrelated files were changed
* [ ] 🏗️ Build succeeds
* [ ] 🧪 Tests pass
* [ ] 🤖 CI passes
* [ ] 📚 Documentation was updated where necessary
* [ ] 🖼️ Screenshots/recordings were attached when applicable
* [ ] 🌿 PR targets `dev`
* [ ] 🏷️ Appropriate labels were added
* [ ] 👀 Required reviewer was assigned
* [ ] 👤 PR owner/assignee was assigned

---

## 🎫 Related Issue / Task

**Closes:** `<issue / ticket / task>`

**Reference:** `<link or identifier>`
