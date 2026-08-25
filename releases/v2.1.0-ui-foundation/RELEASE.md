# NorthStar Operations Intelligence 2.1.0 — UI Foundation

Finalized centralized UI Foundation migration.

## Validation

- Clean Java 21 build using `javac -Xlint:all`
- 18 unique business modules/routes
- Complete modular/UI regression suite
- Real retained Locations/Routes theme regression
- JAR ZIP integrity and duplicate-entry checks
- Startup smoke test under a virtual display
- Source scan for process execution, Java object deserialization, embedded private keys and obvious hard-coded credentials
- Packaged OAuth client ID/client secret fingerprint scan

## Exact source snapshot

`source-project.tar.gz.b64` is a base64-encoded gzip tar archive of the exact 2.1.0 source tree used for the finalized artifact. Decode with:

```bash
base64 -d source-project.tar.gz.b64 > source-project.tar.gz
tar -xzf source-project.tar.gz
```

SHA-256 of the decoded `source-project.tar.gz`:

`86010077b715f587240cc06f9512a1be158358857acca119751820f75f9b08a6`

The standard UI ownership rule for this release is: **modules own behavior; UI Foundation owns standard appearance.**
