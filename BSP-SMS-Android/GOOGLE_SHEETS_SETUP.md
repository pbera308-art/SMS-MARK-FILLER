# Google Sheet + Google Apps Script Setup (Free / Serverless)

## 1. Create the Google Sheet
1. Open [Google Sheets](https://sheets.google.com) and create a new spreadsheet named **`BSP-SMS Users`**.
2. Rename the bottom sheet tab from `Sheet1` to **`Users`** (or let `Code.gs` auto-create it on first request).
3. Add these exact header columns in **Row 1 (`A1:I1`)**:
   - `A1`: `user_id`
   - `B1`: `phone_number`
   - `C1`: `password`
   - `D1`: `status`
   - `E1`: `expiry_date`
   - `F1`: `active_device_id`
   - `G1`: `last_login`
   - `H1`: `last_activity`
   - `I1`: `created_at`

## 2. Deploy Google Apps Script
1. Inside the Google Sheet, click **Extensions → Apps Script**.
2. Delete any starter code in `Code.gs` and paste the full contents of `google-apps-script/Code.gs`.
3. Click **Save** (`Ctrl+S`).
4. Click **Deploy → New deployment**.
5. Select type: **Web app**.
   - **Description:** `BSP-SMS Auth v1`
   - **Execute as:** `Me`
   - **Who has access:** `Anyone`
6. Click **Deploy**, authorize permissions for Google Sheets, and copy the **Web app URL** (`https://script.google.com/macros/s/.../exec`).

## 3. Connect the URL to the Android App
You can connect the Web app URL in **either** of two ways:
- **Option A (Built into APK):** Open `BSP-SMS-Android/app/build.gradle.kts` and replace `https://script.google.com/macros/s/REPLACE_WITH_YOUR_DEPLOYMENT_ID/exec` in `APPS_SCRIPT_URL`, then build the APK via GitHub Actions.
- **Option B (No rebuild needed):** Install the APK, open **Admin Login** (`ID: 9679843774`, `Pass: 9933115114`), open the left drawer → **Admin Monitor**, paste your `/exec` URL into **Apps Script Web App URL**, and tap **Save URL**.

## 4. Manual Activation & Device Reset
- **From the in-app Admin Monitor:** Tap **Activate 30d** next to any user to set `status = active` and `expiry_date = today + 30 days`. Tap **Reset Device** to clear `active_device_id` so the user can move to a new phone.
- **Directly in Google Sheets:** Change `status` (`D`) to `active` and set `expiry_date` (`E`) in `YYYY-MM-DD` format (e.g., `2026-12-20`), or clear `active_device_id` (`F`) to reset device lock.
