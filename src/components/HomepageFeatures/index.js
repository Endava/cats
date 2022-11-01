import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Smart',
    Svg: require('@site/static/img/smart.svg').default,
    description: (
      <>
          CATS is more than a fuzzing tool.
          Outside the typical fuzzing cases that generates random input,
          CATS also generates negative testing scenarios based on data types and structural constrains.
      </>
    ),
  },
  {
    title: 'Fast',
    Svg: require('@site/static/img/fast.svg').default,
    description: (
      <>
        CATS is fast. Really fast. It automatically generates, runs
          and reports thousands of tests within minutes. No coding required.
      </>
    ),
  },
    {
        title: 'Configurable',
        Svg: require('@site/static/img/settings.svg').default,
        description: (
            <>
                CATS has more than 30 configuration options. You can match or ignore HTTP response
                codes, response bodies, API paths, fuzzers, HTTP methods, fine tune reporting and so on.
            </>
        ),
    }
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
