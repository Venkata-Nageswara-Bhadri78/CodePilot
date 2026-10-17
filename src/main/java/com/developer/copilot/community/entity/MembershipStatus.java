package com.developer.copilot.community.entity;

/**
 * Current state of one user's membership in one community.
 * Leaving sets {@link #LEFT}; rejoining the same row sets {@link #ACTIVE} again.
 * There is no deleted or expired state.
 */
public enum MembershipStatus {
    ACTIVE,
    LEFT
}
