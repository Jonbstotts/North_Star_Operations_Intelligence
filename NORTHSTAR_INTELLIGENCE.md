# NorthStar Intelligence — v1.7.0

NorthStar Intelligence is the local AI layer for North Star Operations Intelligence.

## First-release capabilities

- Compact AI assistant injected into a free dashboard grid region without replacing existing modules.
- Full `NorthStar Intelligence` sidebar workspace.
- Ask NorthStar conversation view for policies, schedules, KPIs, shipments, traffic/weather data and other indexed NorthStar records.
- Local Knowledge Library with multi-file import and drag/drop.
- Supported first-release knowledge files: TXT, MD, CSV, JSON, LOG, DOCX, and text-based PDF when the optional `pdftotext` utility is available.
- Uploaded documents are copied to `~/.northstar-operations-intelligence/ai/knowledge` and are not packaged into the JAR.
- Eligible NorthStar operational CSV/JSON/text history is discovered locally and supplied as retrieval context.
- Local Ollama connection defaults to `http://127.0.0.1:11434` with model `llama3.2:3b`.
- No cloud AI API is required.
- Answers display the local source files used for retrieval.

## Permission model

Three permissions are introduced:

- `AI_ASSISTANT` — access to the AI workspace and dashboard assistant.
- `AI_KNOWLEDGE_ADMIN` — add/remove documents in the shared local knowledge library.
- `AI_EMPLOYEE_METRICS` — allows employee performance/attendance/training data to be considered by AI retrieval.

Administrators receive all three automatically. Management receives all three through the v1.7 role template. Operations receives only AI Assistant by default. Custom users can be assigned the permissions individually through the existing Users & Access permission editor.

## Privacy boundary

NorthStar sends generated prompts only to the configured Ollama URL. The default URL is loopback (`127.0.0.1`), keeping questions, retrieved CSV rows, policies and model responses on the local workstation. Credential/config/user/audit files are explicitly excluded from automatic operational-data discovery.

## Retrieval behavior

The first release uses deterministic local document chunking and keyword relevance ranking before invoking the language model. This keeps the source set inspectable and avoids introducing an external vector database. A later release can add local embeddings/vector indexing without changing the workspace or permission model.

## PDF note

Java 21 does not contain a native PDF text parser. To keep this first release dependency-free, PDF import uses a locally installed `pdftotext` utility when available. TXT, MD, CSV, JSON, LOG and DOCX require no additional dependency.
