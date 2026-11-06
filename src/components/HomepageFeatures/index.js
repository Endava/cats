import React from 'react';
import Link from '@docusaurus/Link';
import styles from './styles.module.css';

const WORKFLOW_STEPS = [
  {
    step: '01',
    title: 'Analyze & Smoke-Probe Your API',
    description:
      'Inspect your contract’s 0–100 Fuzzability Readiness Score and producer-to-consumer resource chains offline, then run a 1-request-per-operation smoke probe to verify connectivity, routing, and authentication.',
    code: 'cats analyze -c openapi.yml\ncats smoke -c openapi.yml -s http://localhost:8080',
    link: '/docs/getting-started/running-cats',
    linkLabel: 'Pre-flight Checks →',
  },
  {
    step: '02',
    title: 'Fuzz in Stateful Execution Waves',
    description:
      'Execute 200+ fuzzers across 4 breadth-first waves (seed, coverage, crash, consistency) while automatically learning resource IDs and ETags from 2XX responses and feeding them into downstream requests.',
    code: 'cats -c openapi.yml -s http://localhost:8080 --profile ci\ncats -c openapi.yml -s http://localhost:8080 --profile security',
    link: '/docs/getting-started/fuzzer-profiles',
    linkLabel: 'Fuzzer Profiles →',
  },
  {
    step: '03',
    title: 'Author Declarative Functional Flows',
    description:
      'Write stateful multi-step CRUD workflows, cross-field correlation rules, async polling assertions, and data-driven forEach matrices in minimal YAML—CATS generates the rest of every request from your schema.',
    code: 'cats run -c openapi.yml -s http://localhost:8080 flow.yml\ncats -c openapi.yml -s http://localhost:8080 --correlations rules.yml',
    link: '/docs/fuzzers/special-fuzzers/functional-fuzzer',
    linkLabel: 'Functional Flows →',
  },
  {
    step: '04',
    title: 'Triage Root Causes & Verify Fixes',
    description:
      'Cluster failures into deduplicated root causes, auto-mine refData.yml and correlations.yml from recorded traffic, replay stateful producer chains, and verify fixes live against the server.',
    code: 'cats triage -r cats-report --verify -s http://localhost:8080\ncats replay Test42 --stateful --export-flow flow.yml',
    link: '/docs/getting-started/interpreting-results',
    linkLabel: 'Triage & Replay →',
  },
];

const CAPABILITIES = [
  {
    title: 'Automatic Resource Correlation',
    body: 'Orders root POST producers ahead of dependent endpoints, learns IDs and ETags from 2XX responses, validates candidates against target schema patterns, and defers DELETE teardown until the final wave.',
    to: '/docs/advanced-topics/runtime-resource-reuse',
  },
  {
    title: 'Deep Schema & Constraint Fuzzing',
    body: 'Tests numeric and string boundaries, exclusiveMinimum/Maximum, multipleOf, minItems/maxItems, minProperties, const/not, ambiguous oneOf branches, type coercion, and Unicode normalization.',
    to: '/docs/fuzzers/field-fuzzers',
  },
  {
    title: 'OWASP API Security Top 10',
    body: 'Detects SQL, NoSQL, LDAP, Command, SSTI, XXE, XSS, SSRF, and Path Traversal injections, BOLA/IDOR, readOnly and undeclared Mass Assignment, HTTP Parameter Pollution, and verb tunneling.',
    to: '/docs/getting-started/fuzzer-profiles',
  },
  {
    title: 'Cross-Field Correlations',
    body: 'Express conditional when/set/omit dependencies across sibling fields and arrays so both functional flows and negative fuzzers satisfy domain invariants and test conditionally required fields.',
    to: '/docs/fuzzers/special-fuzzers/functional-fuzzer#cross-field-correlations-correlate-and---correlations',
  },
  {
    title: 'Interactive Terminal UI',
    body: 'Follow live wave progress, filter by result reason or response code, inspect full request/response payloads, and copy one-click curl reproducers directly from your terminal with --tui.',
    to: '/docs/getting-started/tui',
  },
  {
    title: 'Built-In AI Coding Agent Skills',
    body: 'Install ready-made CATS skills for Devin, Claude Code, Codex, OpenCode, and Cursor with a single command (cats init --skills) to automate API auditing, fuzzing, and test authoring.',
    to: '/docs/commands-and-arguments/sub-commands',
  },
];

export default function HomepageFeatures() {
  return (
    <div className={styles.wrapper}>
      <section className={styles.section}>
        <div className="container">
          <div className={styles.sectionHeader}>
            <div className={styles.eyebrow}>Workflow</div>
            <h2 className={styles.sectionTitle}>From OpenAPI spec to verified fix in four commands.</h2>
            <p className={styles.sectionSubtitle}>
              Every subcommand is designed for both interactive developer workflows and machine-readable JSON (`-j`) automation.
            </p>
          </div>

          <div className={styles.workflowGrid}>
            {WORKFLOW_STEPS.map((item) => (
              <div key={item.step} className={styles.workflowCard}>
                <div className={styles.workflowTop}>
                  <span className={styles.stepNumber}>{item.step}</span>
                  <Link className={styles.cardLink} to={item.link}>
                    {item.linkLabel}
                  </Link>
                </div>
                <h3 className={styles.cardTitle}>{item.title}</h3>
                <p className={styles.cardDescription}>{item.description}</p>
                <pre className={styles.cardCode}>
                  <code>{item.code}</code>
                </pre>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className={styles.sectionAlt}>
        <div className="container">
          <div className={styles.sectionHeader}>
            <div className={styles.eyebrow}>Architecture</div>
            <h2 className={styles.sectionTitle}>Engineered for real-world APIs.</h2>
            <p className={styles.sectionSubtitle}>
              Compiled to a standalone GraalVM native binary with deterministic seeding, multi-content-type negotiation, and zero external runtime dependencies.
            </p>
          </div>

          <div className={styles.bentoGrid}>
            {CAPABILITIES.map((cap) => (
              <Link key={cap.title} to={cap.to} className={styles.bentoCard}>
                <h3 className={styles.bentoTitle}>{cap.title}</h3>
                <p className={styles.bentoBody}>{cap.body}</p>
              </Link>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
