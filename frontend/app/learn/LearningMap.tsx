"use client";
import { topics } from "../finwiz/FinwizReactor";
import styles from "./learn.module.css";
export default function LearningMap() { return <section className={styles.learningMap} id="learning-map"><h2>Your learning map</h2><p>Choose a topic to explore with Finwiz.</p><div className={styles.grid}>{topics.map((topic, index) => <a key={topic.id} href={`/finwiz?topic=${topic.id}`}><small>{String(index + 1).padStart(2,"0")}</small><h3>{topic.label}</h3><p>{topic.description}</p><strong>Explore topic →</strong></a>)}</div></section>; }
