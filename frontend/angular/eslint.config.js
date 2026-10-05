// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  {
    // index.html is the static host page, not an Angular component template:
    // it is not compiled/translated through Angular's i18n pipeline, so it is
    // excluded from linting entirely (rather than just from specific rules,
    // since it would otherwise fall through to the default JS parser and fail).
    ignores: ['**/index.html'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      // the type-checked variants need the type checker (languageOptions below): they are what
      // catches an un-awaited promise, a comparison that is always false, an unsafe any...
      tseslint.configs.recommendedTypeChecked,
      tseslint.configs.stylisticTypeChecked,
      angular.configs.tsRecommended,
    ],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: __dirname,
      },
    },
    processor: angular.processInlineTemplates,
    rules: {
      // Angular's Validators.required & co are static methods passed by reference: no `this`
      '@typescript-eslint/unbound-method': ['error', { ignoreStatic: true }],
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {
      '@angular-eslint/template/i18n': [
        'error',
        {
          // PrimeNG / pf-ui technical inputs (icon classes, severities, option keys, ids) and the
          // application's brand name are not user-facing text. Remix icons are <i class="ri-...">
          // elements without text, so nothing to exempt for them.
          ignoreAttributes: [
            'icon',
            'severity',
            'inputId',
            'optionLabel',
            'optionValue',
            'display',
            'headerTitle',
            'headerDomain',
            'headerTitleTargetUrl',
            // id references, not text
            'aria-labelledby',
            // link relation keywords (noopener...), not text
            'rel',
            // PrimeNG overlay placement ('body'), not text
            'appendTo',
            // PrimeNG styling and table keys, not text
            'styleClass',
            'size',
            'dataKey',
            // this app's area of a route ('applications', 'users'), not text
            'area',
          ],
        },
      ],
    },
  },
]);
