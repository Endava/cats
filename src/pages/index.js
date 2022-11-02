import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';

import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <h1 className="Title">
        <div className="line">
          <span className="hero__title">
            &nbsp;REST API&nbsp;
          </span>
          <span className="important">
            fuzzer&nbsp;
          </span>
          <span className="hero__title">
            and
          </span>
        </div>
        <div className="line">
           <span className="hero__title">
            &nbsp;&nbsp;
          </span>
           <span className="important">
            negative testing&nbsp;
          </span>
          <span className="hero__title">
              tool
          </span>
          <span>&nbsp;</span>
        </div>
          <div>
              &nbsp;
          </div>
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/docs/intro">
            Get Started with CATS - 1min ⏱️
          </Link>
        </div>
        </h1>
      </div>
    </header>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={`REST API fuzzer and negative testing tool`}
      description="CATS is a REST API Fuzzer and negative testing tool for OpenAPI endpoints. CATS automatically generates, runs and reports tests with minimum configuration and no coding effort. Tests are self-healing and do not require maintenance.">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
