import React from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';

import styles from './index.module.css';

const STATS = [
  { value: '190+', label: 'Field, Header & HTTP Fuzzers' },
  { value: '85+', label: 'Semantic Format Generators' },
  { value: '36', label: 'Continuous Random Mutators' },
  { value: 'Zero', label: 'Lines of Test Code Required' },
];

function HomepageHero() {
  return (
    <header className={styles.hero}>
      <div className={styles.heroGlow} aria-hidden="true" />
      <div className="container">
        <div className={styles.heroInner}>
          <div className={styles.badge}>
            <span className={styles.badgeDot} />
            <span>CATS · Contract API Testing &amp; Security</span>
          </div>

          <h1 className={styles.heroTitle}>
            Autonomous API testing,{' '}
            <span className={styles.heroTitleAccent}>built for precision.</span>
          </h1>

          <p className={styles.heroSubtitle}>
            CATS reads your OpenAPI specification, resolves resource dependencies,
            and executes thousands of self-healing boundary, security, and stateful
            functional tests in seconds—with zero test code.
          </p>

          <div className={styles.actions}>
            <Link className={styles.primaryButton} to="/docs/intro">
              Get Started
              <span className={styles.buttonArrow} aria-hidden="true">→</span>
            </Link>
            <Link className={styles.secondaryButton} to="/docs/fuzzers">
              Explore Capabilities
            </Link>
          </div>

          <div className={styles.terminalCard}>
            <div className={styles.terminalHeader}>
              <div className={styles.terminalDots} aria-hidden="true">
                <span />
                <span />
                <span />
              </div>
              <div className={styles.terminalTitle}>Terminal — cats workflow</div>
              <div className={styles.terminalTag}>zsh</div>
            </div>
            <pre className={styles.terminalBody}>
              <code>
                <span className={styles.cmdComment}># 1. Install via Homebrew</span>
                {'\n'}
                <span className={styles.cmdPrompt}>$</span> brew tap endava/tap &amp;&amp; brew install cats
                {'\n\n'}
                <span className={styles.cmdComment}># 2. Score contract fuzzability (0–100) and verify live connectivity</span>
                {'\n'}
                <span className={styles.cmdPrompt}>$</span> cats analyze -c openapi.yml
                {'\n'}
                <span className={styles.cmdPrompt}>$</span> cats smoke -c openapi.yml -s http://localhost:8080 -H <span className={styles.cmdString}>"Authorization=Bearer $$API_TOKEN"</span>
                {'\n\n'}
                <span className={styles.cmdComment}># 3. Run stateful wave fuzzing and cluster root causes</span>
                {'\n'}
                <span className={styles.cmdPrompt}>$</span> cats -c openapi.yml -s http://localhost:8080 --profile ci
                {'\n'}
                <span className={styles.cmdPrompt}>$</span> cats triage -r cats-report -c openapi.yml --verify -s http://localhost:8080
              </code>
            </pre>
          </div>

          <div className={styles.statsGrid}>
            {STATS.map((item) => (
              <div key={item.label} className={styles.statCell}>
                <div className={styles.statValue}>{item.value}</div>
                <div className={styles.statLabel}>{item.label}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </header>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.tagline}
      description="CATS is an autonomous REST API fuzzer, OpenAPI contract linter, and stateful functional testing tool. Generate, run, and triage thousands of self-healing API tests without writing code.">
      <HomepageHero />
      <main>
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
