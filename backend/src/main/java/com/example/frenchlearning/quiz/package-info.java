/**
 * Quiz content, scoring, submission, and immutable attempt-history use cases.
 *
 * <p>This feature owns quiz-domain invariants and quiz persistence. Public
 * mappings must keep correct answers private before submission and preserve
 * historical result snapshots after content changes.</p>
 */
package com.example.frenchlearning.quiz;
