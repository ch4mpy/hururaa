# Hurura'a frontend

Angular 21 workspace (standalone, zoneless) with:

- `projects/hururaa`: the SPA, built with the components of the Polynesian administration's design
  system [`pf-ui`](https://bin.gov.pf/artifactory/api/npm/npm/pf-ui/-/pf-ui-21.0.14.tgz) (PrimeNG
  21, PrimeFlex) and [Remix Icon](https://remixicon.com/) functional icons;
- `projects/api/gateway` and `projects/api/hururaa-api`: clients generated from the backend's
  OpenAPI specs (`npm run api`, never edited by hand).

```bash
npm install          # needs access to bin.gov.pf for pf-ui
npm run api          # regenerate and build the API clients from ../../backend/openapi/out
npm run start        # fr (4200) and en (4205) dev servers, behind https://host.docker.internal/{fr,en}/
npm run test:ci      # Vitest, with coverage
npm run lint
npm run build        # localized production build: dist/hururaa/browser/{fr,en}
```

Texts are translated with `@angular/localize` (`npm run extract-i18n`, then
`src/locale/messages.en-US.xlf`); pf-ui's own labels come from ngx-translate, with the language
of the build.
