/**
 * BSP-SMS Serverless Auth, 30-Day Activation, Single-Device Lock & Admin Monitor
 * Deploy as Web App:
 *   Execute as: Me
 *   Who has access: Anyone
 */

const SHEET_NAME = "Users";
const TIMEZONE = "Asia/Kolkata";
const ADMIN_ID = "9679843774";
const ADMIN_PASSWORD = "9933115114";

const HEADERS = [
  "user_id",
  "phone_number",
  "password",
  "status",
  "expiry_date",
  "active_device_id",
  "last_login",
  "last_activity",
  "created_at"
];

function doGet() {
  return jsonOut({ ok: true, message: "BSP-SMS Auth Service Ready" });
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const raw = (e && e.postData && e.postData.contents) ? e.postData.contents : "{}";
    const body = JSON.parse(raw);
    const action = String(body.action || "").trim();

    switch (action) {
      case "register":
        return jsonOut(handleRegister(body));
      case "login":
        return jsonOut(handleLogin(body));
      case "check_session":
        return jsonOut(handleCheckSession(body));
      case "update_activity":
        return jsonOut(handleUpdateActivity(body));
      case "admin_login":
        return jsonOut(handleAdminLogin(body));
      case "admin_list_users":
        return jsonOut(handleAdminListUsers(body));
      case "admin_activate_user":
        return jsonOut(handleAdminActivateUser(body));
      case "admin_deactivate_user":
        return jsonOut(handleAdminDeactivateUser(body));
      case "admin_reset_device":
        return jsonOut(handleAdminResetDevice(body));
      default:
        return jsonOut({ ok: false, code: "UNKNOWN_ACTION", message: "Unsupported action: " + action });
    }
  } catch (err) {
    return jsonOut({ ok: false, code: "SERVER_ERROR", message: String(err && err.message ? err.message : err) });
  } finally {
    lock.releaseLock();
  }
}

/**
 * Optional Google Sheets installable/simple trigger:
 * When you manually change status -> "active" in the Google Sheet and expiry_date is empty,
 * this automatically sets expiry_date to 30 days from today.
 */
function onEdit(e) {
  if (!e || !e.range) return;
  const sheet = e.range.getSheet();
  if (sheet.getName() !== SHEET_NAME) return;
  const row = e.range.getRow();
  const col = e.range.getColumn();
  if (row <= 1 || col !== 4) return; // Column 4 = status

  const newStatus = String(e.value || "").trim().toLowerCase();
  if (newStatus === "active") {
    const expiryCell = sheet.getRange(row, 5);
    const currentExpiry = formatDateValue(expiryCell.getValue());
    if (!currentExpiry) {
      expiryCell.setValue(addDaysStr(30));
    }
    sheet.getRange(row, 8).setValue("Activated 30d in Sheet (" + nowStr() + ")");
  }
}

function getUsersSheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow(HEADERS);
    sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
  } else if (sheet.getLastRow() === 0) {
    sheet.appendRow(HEADERS);
    sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function normalizePhone(raw) {
  const digits = String(raw || "").replace(/\D+/g, "");
  if (digits.length > 10 && digits.startsWith("91")) {
    return digits.slice(digits.length - 10);
  }
  return digits;
}

function hashPassword(plain) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(plain), Utilities.Charset.UTF_8);
  const hex = bytes.map(b => ((b < 0 ? b + 256 : b).toString(16)).padStart(2, "0")).join("");
  return "sha256:" + hex;
}

function verifyPassword(stored, inputPlain) {
  const s = String(stored || "");
  const p = String(inputPlain || "");
  if (!s) return false;
  if (s.startsWith("sha256:")) {
    return s === hashPassword(p);
  }
  // Supports manual plaintext password edits in the Google Sheet
  return s === p;
}

function todayStr() {
  return Utilities.formatDate(new Date(), TIMEZONE, "yyyy-MM-dd");
}

function nowStr() {
  return Utilities.formatDate(new Date(), TIMEZONE, "yyyy-MM-dd HH:mm:ss");
}

function addDaysStr(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return Utilities.formatDate(d, TIMEZONE, "yyyy-MM-dd");
}

function formatDateValue(val) {
  if (!val) return "";
  if (Object.prototype.toString.call(val) === "[object Date]" && !isNaN(val.getTime())) {
    return Utilities.formatDate(val, TIMEZONE, "yyyy-MM-dd");
  }
  const s = String(val).trim();
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) {
    return s.slice(0, 10);
  }
  return s;
}

function formatTimestampValue(val) {
  if (!val) return "";
  if (Object.prototype.toString.call(val) === "[object Date]" && !isNaN(val.getTime())) {
    return Utilities.formatDate(val, TIMEZONE, "yyyy-MM-dd HH:mm:ss");
  }
  return String(val).trim();
}

function rowToUser(row) {
  return {
    userId: String(row[0] || "").trim(),
    phone: String(row[1] || "").trim(),
    status: String(row[3] || "inactive").trim().toLowerCase(),
    expiryDate: formatDateValue(row[4]),
    activeDeviceId: String(row[5] || "").trim(),
    lastLogin: formatTimestampValue(row[6]),
    lastActivity: String(row[7] || "").trim(),
    createdAt: formatTimestampValue(row[8])
  };
}

function handleRegister(body) {
  const userId = String(body.userId || "").trim().toUpperCase();
  const phone = normalizePhone(body.phone);
  const password = String(body.password || "");
  const deviceId = String(body.deviceId || "").trim();

  if (!userId || userId.length < 4) {
    return { ok: false, code: "INVALID_INPUT", message: "Valid User ID is required." };
  }
  if (!phone || phone.length < 10) {
    return { ok: false, code: "INVALID_PHONE", message: "Please enter a valid 10-digit phone number." };
  }
  if (!password || password.length < 4) {
    return { ok: false, code: "WEAK_PASSWORD", message: "Password must be at least 4 characters." };
  }

  const sheet = getUsersSheet();
  const lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    const values = sheet.getRange(2, 1, lastRow - 1, HEADERS.length).getValues();
    for (let i = 0; i < values.length; i++) {
      const existingId = String(values[i][0] || "").trim().toUpperCase();
      const existingPhone = normalizePhone(values[i][1]);
      if (existingId === userId) {
        return { ok: false, code: "USER_ID_EXISTS", message: "User ID already exists. Generate a new ID and try again." };
      }
      if (existingPhone === phone) {
        return { ok: false, code: "PHONE_EXISTS", message: "This phone number is already registered. Please use General Login." };
      }
    }
  }

  const createdAt = nowStr();
  sheet.appendRow([
    userId,
    phone,
    hashPassword(password),
    "inactive",
    "",
    deviceId,
    "",
    "Registered (awaiting admin activation)",
    createdAt
  ]);

  // Force phone_number column as plain text to preserve formatting
  const newRowIdx = sheet.getLastRow();
  sheet.getRange(newRowIdx, 2).setNumberFormat("@").setValue(phone);

  return {
    ok: true,
    code: "REGISTERED",
    message: "Registration successful. Your account is inactive until activated by Admin.",
    user: {
      userId: userId,
      phone: phone,
      status: "inactive",
      expiryDate: "",
      activeDeviceId: deviceId,
      lastLogin: "",
      lastActivity: "Registered",
      createdAt: createdAt
    }
  };
}

function findUserRow(sheet, identifier) {
  const lastRow = sheet.getLastRow();
  if (lastRow <= 1) return null;

  const idUpper = String(identifier || "").trim().toUpperCase();
  const phoneNorm = normalizePhone(identifier);
  const values = sheet.getRange(2, 1, lastRow - 1, HEADERS.length).getValues();

  for (let i = 0; i < values.length; i++) {
    const rowUserId = String(values[i][0] || "").trim().toUpperCase();
    const rowPhone = normalizePhone(values[i][1]);
    if ((idUpper && rowUserId === idUpper) || (phoneNorm && phoneNorm.length >= 10 && rowPhone === phoneNorm)) {
      return { rowIndex: i + 2, row: values[i] };
    }
  }
  return null;
}

function handleLogin(body) {
  const identifier = String(body.identifier || "").trim();
  const password = String(body.password || "");
  const deviceId = String(body.deviceId || "").trim();

  if (!identifier || !password) {
    return { ok: false, code: "INVALID_INPUT", message: "Enter User ID / Phone Number and Password." };
  }
  if (!deviceId) {
    return { ok: false, code: "DEVICE_REQUIRED", message: "Device ID unavailable." };
  }

  const sheet = getUsersSheet();
  const found = findUserRow(sheet, identifier);
  if (!found) {
    return { ok: false, code: "NOT_FOUND", message: "Account not found. Please register first." };
  }

  const { rowIndex, row } = found;
  if (!verifyPassword(row[2], password)) {
    return { ok: false, code: "BAD_PASSWORD", message: "Incorrect password." };
  }

  const user = rowToUser(row);
  if (user.status !== "active") {
    return {
      ok: false,
      code: "INACTIVE",
      message: "Your account (" + user.userId + ") is inactive. Contact Admin for 30-day activation.",
      user: user
    };
  }

  // If Admin manually set status='active' in Sheet without filling expiry_date, auto-set 30 days
  let expiryDate = user.expiryDate;
  if (!expiryDate) {
    expiryDate = addDaysStr(30);
    sheet.getRange(rowIndex, 5).setNumberFormat("@").setValue(expiryDate);
    user.expiryDate = expiryDate;
  }

  if (todayStr() > expiryDate) {
    return {
      ok: false,
      code: "EXPIRED",
      message: "Your 1-month (30-day) subscription expired on " + expiryDate + ". Contact Admin to renew.",
      user: user
    };
  }

  if (user.activeDeviceId && user.activeDeviceId !== deviceId) {
    return {
      ok: false,
      code: "DEVICE_LOCKED",
      message: "Account locked to another device (" + user.activeDeviceId.slice(0, 8) + "...). Ask Admin to reset device lock.",
      user: user
    };
  }

  const loginTs = nowStr();
  if (!user.activeDeviceId) {
    sheet.getRange(rowIndex, 6).setValue(deviceId);
    user.activeDeviceId = deviceId;
  }
  sheet.getRange(rowIndex, 7).setValue(loginTs);
  sheet.getRange(rowIndex, 8).setValue("Logged in (" + loginTs + ")");
  user.lastLogin = loginTs;
  user.lastActivity = "Logged in (" + loginTs + ")";

  return {
    ok: true,
    code: "OK",
    message: "Login successful.",
    user: user
  };
}

function handleCheckSession(body) {
  const userId = String(body.userId || "").trim();
  const deviceId = String(body.deviceId || "").trim();
  if (!userId || !deviceId) {
    return { ok: false, code: "INVALID_SESSION", message: "Session invalid." };
  }

  const sheet = getUsersSheet();
  const found = findUserRow(sheet, userId);
  if (!found) {
    return { ok: false, code: "NOT_FOUND", message: "Account no longer exists." };
  }

  const { rowIndex, row } = found;
  const user = rowToUser(row);

  if (user.status !== "active") {
    return {
      ok: false,
      code: "INACTIVE",
      message: "Your account is currently inactive. Contact Admin for activation.",
      user: user
    };
  }

  if (!user.expiryDate || todayStr() > user.expiryDate) {
    return {
      ok: false,
      code: "EXPIRED",
      message: "Your 1-month (30-day) subscription expired on " + (user.expiryDate || "N/A") + ". Contact Admin to renew.",
      user: user
    };
  }

  if (!user.activeDeviceId || user.activeDeviceId !== deviceId) {
    return {
      ok: false,
      code: "DEVICE_LOCKED",
      message: "Device lock mismatch or reset by Admin. Please sign in again.",
      user: user
    };
  }

  sheet.getRange(rowIndex, 8).setValue("Session verified (" + nowStr() + ")");
  return { ok: true, code: "OK", message: "Session valid.", user: user };
}

function handleUpdateActivity(body) {
  const userId = String(body.userId || "").trim();
  const deviceId = String(body.deviceId || "").trim();
  const activity = String(body.activity || "Active").trim();

  const sheet = getUsersSheet();
  const found = findUserRow(sheet, userId);
  if (!found) {
    return { ok: false, code: "NOT_FOUND", message: "Account not found." };
  }
  const { rowIndex, row } = found;
  const user = rowToUser(row);
  if (user.activeDeviceId && deviceId && user.activeDeviceId !== deviceId) {
    return { ok: false, code: "DEVICE_LOCKED", message: "Device lock mismatch." };
  }
  sheet.getRange(rowIndex, 8).setValue(activity + " (" + nowStr() + ")");
  return { ok: true, code: "OK", message: "Activity updated." };
}

function verifyAdmin(body) {
  return String(body.adminId || "").trim() === ADMIN_ID && String(body.adminPassword || "") === ADMIN_PASSWORD;
}

function handleAdminLogin(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, code: "UNAUTHORIZED", message: "Invalid Admin ID or Password." };
  }
  return { ok: true, code: "ADMIN_OK", message: "Admin verified." };
}

function handleAdminListUsers(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const lastRow = sheet.getLastRow();
  const users = [];
  if (lastRow > 1) {
    const values = sheet.getRange(2, 1, lastRow - 1, HEADERS.length).getValues();
    for (let i = 0; i < values.length; i++) {
      if (!values[i][0] && !values[i][1]) continue;
      users.push(rowToUser(values[i]));
    }
  }
  return { ok: true, code: "OK", message: "Loaded " + users.length + " user(s).", users: users };
}

function handleAdminActivateUser(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const targetUserId = String(body.targetUserId || "").trim();
  const sheet = getUsersSheet();
  const found = findUserRow(sheet, targetUserId);
  if (!found) {
    return { ok: false, code: "NOT_FOUND", message: "User not found: " + targetUserId };
  }

  const expiry = addDaysStr(30);
  const ts = nowStr();
  sheet.getRange(found.rowIndex, 4).setValue("active");
  sheet.getRange(found.rowIndex, 5).setNumberFormat("@").setValue(expiry);
  sheet.getRange(found.rowIndex, 8).setValue("Activated 30d by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, HEADERS.length).getValues()[0];
  return {
    ok: true,
    code: "ACTIVATED",
    message: "Activated " + updatedRow[0] + " until " + expiry,
    user: rowToUser(updatedRow)
  };
}

function handleAdminDeactivateUser(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const targetUserId = String(body.targetUserId || "").trim();
  const sheet = getUsersSheet();
  const found = findUserRow(sheet, targetUserId);
  if (!found) {
    return { ok: false, code: "NOT_FOUND", message: "User not found: " + targetUserId };
  }

  const ts = nowStr();
  sheet.getRange(found.rowIndex, 4).setValue("inactive");
  sheet.getRange(found.rowIndex, 8).setValue("Deactivated by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, HEADERS.length).getValues()[0];
  return {
    ok: true,
    code: "DEACTIVATED",
    message: "Deactivated " + updatedRow[0],
    user: rowToUser(updatedRow)
  };
}

function handleAdminResetDevice(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const targetUserId = String(body.targetUserId || "").trim();
  const sheet = getUsersSheet();
  const found = findUserRow(sheet, targetUserId);
  if (!found) {
    return { ok: false, code: "NOT_FOUND", message: "User not found: " + targetUserId };
  }

  const ts = nowStr();
  sheet.getRange(found.rowIndex, 6).setValue("");
  sheet.getRange(found.rowIndex, 8).setValue("Device lock reset by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, HEADERS.length).getValues()[0];
  return {
    ok: true,
    code: "DEVICE_RESET",
    message: "Device lock cleared for " + updatedRow[0],
    user: rowToUser(updatedRow)
  };
}

function jsonOut(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
