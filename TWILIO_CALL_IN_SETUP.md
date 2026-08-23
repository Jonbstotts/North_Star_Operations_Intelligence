## North Star workspace organization

Call-In is now an application-level workspace in the left navigation. Use **Call-In → Settings** for the master enable/disable control, webhook/Twilio/SendGrid configuration and management recipients. Use **Call-In → Activity** and **History** for system-wide records, and **Call-In → Testing** for local validation. Employee Operations keeps only employee-specific phone/PIN and per-employee attendance history.

# North Star Operations Intelligence — Call-In Service

## Modes

### LOCAL_TEST

No Twilio account is required.

Management can enter an employee number, employee PIN, and attendance type directly in
Call-In -> Testing. The event is recorded through the same
Employee Operations attendance path used by production calls.

"Simulate + Notify" can also test configured SMS/email notification credentials.

### TWILIO_WEBHOOK

North Star starts a local webhook listener on the configured port.

Twilio Voice URL:

    https://YOUR-PUBLIC-HOST/callin/voice

Method:

    POST

Additional North Star paths are used internally by the TwiML call flow:

    /callin/employee
    /callin/pin
    /callin/type
    /callin/health

## Call flow

1. Employee calls the Twilio number.
2. North Star asks for employee number.
3. North Star asks for the employee call-in PIN.
4. The employee chooses:
   - 1 = Call out
   - 2 = Running late
   - 3 = Leaving early
   - 4 = Other attendance issue
5. North Star writes an AttendanceRecord.
6. Configured management SMS/email notifications are attempted.

## Webhook security

Production webhook requests are validated against X-Twilio-Signature.

North Star uses:

- the Twilio Auth Token,
- the exact configured public HTTPS URL,
- and the submitted POST parameters

to validate each inbound request before any employee information is processed.

The embedded North Star listener is intended to remain behind the site's network boundary.
Expose it only through an approved HTTPS reverse proxy, secure tunnel, serverless function,
or future North Star web service. Do not directly port-forward the host computer to the public
Internet.

## Notification providers

### SMS

Uses Twilio Programmable Messaging.

Required:

- Twilio Account SID
- Twilio Auth Token
- Twilio from-number
- Management destination number(s)

### Email

Uses the SendGrid Mail Send API.

Required:

- SendGrid API key
- Verified sender address
- Management destination email address(es)

Provider credentials are stored in North Star's private encrypted credential store file rather
than the ordinary dashboard configuration.

## Trial / proof-of-concept workflow

For initial testing:

1. Leave North Star in LOCAL_TEST mode while Employee Operations is being configured.
2. Create employee numbers and call-in PINs.
3. Test attendance records locally.
4. Configure a Twilio trial account.
5. Configure a public HTTPS webhook endpoint.
6. Switch to TWILIO_WEBHOOK only after signature validation can succeed.
7. Call the trial number from an allowed/verified test phone.
8. Confirm North Star records the attendance event.
9. Add management notifications only after the call flow is stable.

## Local API usage

North Star's API Usage screen now also records installation-local outbound requests for:

- Twilio management SMS
- SendGrid management email

Provider dashboards remain authoritative for billing/account-wide usage.
