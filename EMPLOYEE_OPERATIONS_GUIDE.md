# ORIVUE 5.0 — Employee Operations

## Purpose

Employee Operations is the management-only personnel system of record for ORIVUE.
It replaces the former duplicate Team Celebrations employee table as the authoritative
source for employee identity and recognition data.

The existing celebration engine remains as a compatibility projection so the Main
Showcase can continue generating birthday, anniversary, and Employee of the Month slides
without maintaining a second employee database.

## Access

Built-in Administrator accounts have all Employee Operations permissions.

Built-in Management accounts receive:

- Employee Operations
- Employee training / qualifications
- Employee attendance / call-ins
- Employee performance
- Employee assignment planning
- Call-in / notification administration

Operations and Display accounts do not receive these permissions by default.

Existing Management accounts from older releases are migrated automatically when they
already possessed the prior employee-information permission.

## Employee profile

Each employee can store:

- Employee number
- Name
- WMS / short name
- Department
- Shift
- Hire date
- Birthday
- Phone number
- Managed employee photo
- Active status
- Birthday-recognition preference
- Anniversary-recognition preference
- Employee of the Month status
- Call-in PIN

Call-in PINs are never stored as plaintext. ORIVUE stores a salted
PBKDF2-HMAC-SHA256 hash.

## Training and qualifications

Training records can describe:

- HazMat / Dangerous Goods
- Equipment qualifications
- Work-area qualifications
- Safety training
- Other certifications

Each record includes a completion date, optional expiration date, trainer, status,
and notes. Expired qualifications are automatically excluded from assignment eligibility.

## Attendance and call-ins

Attendance records support:

- Call out
- Running late
- Leaving early
- Absent
- Scheduled absence
- Other

Records identify their source, such as MANUAL, LOCAL_TEST, or TWILIO_VOICE.

## Performance

Employee performance history supports arbitrary metrics so future SQL/report integrations
can populate LHY, picks, moves, lines, damage metrics, floor denials, quality, or other
site-specific measures.

## Daily assignment recommendations

Management defines duties and the qualification required for each duty.

ORIVUE then:

1. Finds active employees.
2. Removes employees with whole-day attendance events such as call-outs.
3. Removes expired/inactive qualifications.
4. Matches qualified employees to duties.
5. Balances recommendations by preferring the currently least-assigned qualified person.
6. Identifies coverage gaps when no qualified available employee exists.

The generated plan is a recommendation for management review rather than an autonomous
work assignment.

## Recognition migration

On the first 5.0 startup, an installation that has existing Team Celebrations records but
no Employee Operations records migrates those employees automatically.

After migration:

Employee Operations -> compatibility CelebrationConfig -> Main Showcase

Classic Settings no longer writes the retired duplicate celebration table back over the
Employee Operations data.

## Celebration announcement preferences

Employee Operations is the only employee/recognition settings destination. The separate Team Celebrations settings route has been removed.

Each employee has a master **Celebration announcements** preference. When disabled, the employee remains active and their birthday/hire-date data remains stored, but ORIVUE/North Star will not generate birthday, anniversary, or Employee of the Month announcements for that person.

Birthday, anniversary, and Employee of the Month remain individual recognition choices when the master preference is enabled.

## Employee phone numbers

Employee phone entry is human-friendly. Common U.S. formats are accepted, including `205-799-9890`, `(205) 799-9890`, `2057999890`, `1-205-799-9890`, and `+12057999890`.

The application validates the value and stores the canonical provider form internally as E.164 (`+12057999890`). The Employee Operations screen displays the same U.S. number in readable form as `(205) 799-9890`. Twilio SMS destination/from numbers are also normalized immediately before outbound API calls.
