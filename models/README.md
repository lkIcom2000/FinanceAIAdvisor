# Models

Every model bound in an Agent Configuration (`config/agent/*.yaml`, roles `phrasing`, `nlu`, `judge`) plus the
embedding model is listed in `tags.txt` and pinned in `lock.json` (AD-18):

- `tag` -- the Ollama tag the configuration names;
- `digest` -- the tag's digest as Ollama's `/api/tags` reports it (identifies the manifest);
- `ggufSha256` -- the sha256 of the weights layer the manifest pins.

At startup the app compares the digest Ollama serves for every bound model with the lock (the digest pins the
manifest, which pins the weights; `ggufSha256` is recorded for traceability, not re-hashed) and asks the `nlu` and
`judge` models for a small JSON-schema answer; any mismatch stops startup. The app never pulls models itself.

Pull and lock (stack running, from the repository root):

```sh
bash models/update-lock.sh
```

Then restart the app. A model that is imported from a GGUF file instead of pulled gets a checked-in Modelfile
here with the correct chat template.
