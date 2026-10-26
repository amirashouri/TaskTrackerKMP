package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.CommentID
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ttAvatarSize
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttInitialsAvatar
import com.tasktracker.designsystem.ttInitialsFor
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Comment
import com.tasktracker.resources.Res
import com.tasktracker.resources.comment_cancel_reply
import com.tasktracker.resources.comment_composer_hint
import com.tasktracker.resources.comment_composer_title
import com.tasktracker.resources.comment_empty
import com.tasktracker.resources.comment_reply
import com.tasktracker.resources.comment_replying_to
import com.tasktracker.resources.comment_section_label
import com.tasktracker.resources.comment_send
import org.jetbrains.compose.resources.stringResource

/**
 * One work item's comment thread: the conversation, plus the box to add to it.
 *
 * ## Threading is grouped, storage is flat
 *
 * [Comment.parentID] points at the comment being answered, and this view turns that flat
 * list into a tree: top-level comments first, replies indented under the one they answer,
 * at any depth. The store deliberately keeps the list flat (see the domain's `Comment`)
 * so a reply whose parent arrives later — or whose parent lives on another device — can
 * still be written; grouping is a rendering decision and belongs here.
 *
 * ## The composer is absent rather than disabled with no session
 *
 * [canComment] is `false` when no signed-in user is known, and the box does not draw at
 * all. A disabled Send the user can never enable teaches nothing; the domain refuses an
 * unattributed comment on purpose ([Comment.authorID] is non-null), so hiding the
 * affordance is the honest UI for "cannot attribute".
 *
 * **Replying** is a mode of the one composer, not a second box per row: tapping Reply
 * anywhere in the thread redirects the same draft, and [onCancelReply] backs out. One
 * box means one draft, one send button, and no state about which of several boxes has
 * focus.
 */
@Composable
fun CommentThread(
    comments: List<Comment>,
    authorNames: Map<UserID, String>,
    canComment: Boolean,
    draft: String,
    replyTo: CommentID?,
    onDraftChanged: (String) -> Unit,
    onReply: (CommentID) -> Unit,
    onCancelReply: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkItemCommentsTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = stringResource(Res.string.comment_section_label),
            style = ttTypography.captionEmphasised,
            color = ttLabelSecondary(),
        )
        if (comments.isEmpty()) {
            ttText(
                text = stringResource(Res.string.comment_empty),
                style = ttTypography.caption,
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(WorkItemCommentsEmptyTag),
            )
        } else {
            val childrenOf = comments.groupBy { it.parentID }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkItemCommentsListTag),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                for (topLevel in childrenOf[null].orEmpty()) {
                    CommentBranch(
                        comment = topLevel,
                        childrenOf = childrenOf,
                        authorNames = authorNames,
                        onReply = onReply,
                    )
                }
            }
        }
        if (canComment) {
            replyTo?.let { parentID ->
                // The map is keyed by author, so the parent itself has to supply the id —
                // and a parent that is gone (its thread changed while this composer held
                // the target) falls back to the bare word rather than a wrong name.
                val parent = comments.firstOrNull { it.id == parentID }
                val parentLabel = parent
                    ?.let { "“${authorNames[it.authorID] ?: it.authorID.stringValue}”" }
                    ?: stringResource(Res.string.comment_reply)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ttText(
                        text = stringResource(Res.string.comment_replying_to, parentLabel),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ttText(
                        text = stringResource(Res.string.comment_cancel_reply),
                        style = ttTypography.footnote,
                        color = ttLabel(),
                        modifier = Modifier
                            .testTag(WorkItemCommentCancelReplyTag)
                            .clickable(role = Role.Button, onClick = onCancelReply),
                    )
                }
            }
            ProjectsTextField(
                title = stringResource(Res.string.comment_composer_title),
                value = draft,
                onValueChange = onDraftChanged,
                placeholder = stringResource(Res.string.comment_composer_hint),
                modifier = Modifier.testTag(WorkItemCommentComposerTag),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.comment_send),
                    onClick = onSubmit,
                    modifier = Modifier.testTag(WorkItemCommentSendTag),
                )
            }
        }
    }
}

/**
 * One comment and its replies, indented.
 *
 * Replies recurse rather than flattening at one level: a reply to a reply is a real shape
 * the flat store allows, and cutting the tree at depth two would misfile it as a
 * top-level comment merely because it is deep.
 */
@Composable
private fun CommentBranch(
    comment: Comment,
    childrenOf: Map<CommentID?, List<Comment>>,
    authorNames: Map<UserID, String>,
    onReply: (CommentID) -> Unit,
    depth: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WorkItemCommentTag(comment.id)),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            if (depth > 0) {
                // Indentation as spacer at the head of the row, so the disc, name and body
                // move together and the thread's left edge stays a single read.
                androidx.compose.foundation.layout.Spacer(
                    Modifier.padding(start = ttSpacing.xl * depth),
                )
            }
            ttInitialsAvatar(
                initials = ttInitialsFor(authorNames[comment.authorID].orEmpty()),
                size = ttAvatarSize.avatar,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ttText(
                        // The honest fallback is the raw id, the same one the members
                        // list showed when the directory could not name somebody: a
                        // blank author is worse than an ugly one.
                        text = authorNames[comment.authorID] ?: comment.authorID.stringValue,
                        style = ttTypography.footnote,
                        color = ttLabel(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ttText(
                        text = stringResource(Res.string.comment_reply),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                        modifier = Modifier
                            .testTag(WorkItemCommentReplyTag(comment.id))
                            .clickable(role = Role.Button) { onReply(comment.id) },
                    )
                }
                ttText(
                    text = comment.body,
                    style = ttTypography.callout,
                    color = ttLabel(),
                )
            }
        }
        childrenOf[comment.id].orEmpty().forEach { child ->
            CommentBranch(
                comment = child,
                childrenOf = childrenOf,
                authorNames = authorNames,
                onReply = onReply,
                depth = depth + 1,
            )
        }
    }
}

const val WorkItemCommentsTag = "workItem.editor.comments"
const val WorkItemCommentsListTag = "workItem.editor.comments.list"
const val WorkItemCommentsEmptyTag = "workItem.editor.comments.empty"
const val WorkItemCommentComposerTag = "workItem.editor.comments.composer"
const val WorkItemCommentSendTag = "workItem.editor.comments.send"
const val WorkItemCommentCancelReplyTag = "workItem.editor.comments.reply.cancel"

fun WorkItemCommentTag(id: CommentID): String = "workItem.editor.comment.$id"

fun WorkItemCommentReplyTag(id: CommentID): String = "workItem.editor.comment.reply.$id"
