package com.developer.copilot.community.service;

public final class CommunityMessages {

    public static final String NO_MATCH = "No community exists for this posting yet.";

    public static final String EXISTING = "A community already exists for this posting.";

    public static final String UNCERTAIN =
            "This URL is not specific enough to identify one job posting. Open the posting page and use that URL, including any job id.";

    public static final String CONFLICT =
            "These URLs belong to different communities. Remove the URL that does not match this posting.";

    public static final String CREATED = "Community created.";

    public static final String REFINED = "Description refined.";

    public static final String REFINEMENT_KEPT_ORIGINAL =
            "The description could not be refined. The text you sent was kept.";

    public static final String UNKNOWN_FIELD =
            "Send only the documented fields. A user id is not accepted.";

    public static final String DESCRIPTION_REQUIRED = "Description is required.";

    public static final String JOINED = "You joined the community.";

    public static final String ALREADY_MEMBER = "You are already a member of this community.";

    public static final String REJOINED = "You rejoined the community.";

    public static final String LEFT = "You left the community. It remains open.";

    public static final String ALREADY_LEFT = "You have already left this community.";

    public static final String NOT_A_MEMBER = "You are not a member of this community.";

    public static final String MEMBER = "You are a member of this community.";

    public static final String MEMBERSHIP_REQUIRED = "Join this community to continue.";

    public static final String COMMUNITY_UNAVAILABLE = "This community is not available.";

    public static final String INVALID_COMMUNITY_ID = "Community id must be a positive number.";

    public static final String NO_FIELDS = "Send no fields. A user id is not accepted.";

    public static final String LISTED = "Communities you belong to.";

    public static final String DETAILS = "Community details.";

    public static final String MEMBERS = "Community members.";

    private CommunityMessages() {
    }

    public static String tooManyUrls(int maxAlternatives) {
        return "You can add at most " + maxAlternatives + " alternative posting URLs.";
    }
}
