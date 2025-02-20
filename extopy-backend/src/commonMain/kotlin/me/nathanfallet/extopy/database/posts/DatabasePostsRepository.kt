package me.nathanfallet.extopy.database.posts

import kotlinx.datetime.Clock
import me.nathanfallet.extopy.database.Database
import me.nathanfallet.extopy.database.users.FollowersInUsers
import me.nathanfallet.extopy.database.users.Users
import me.nathanfallet.extopy.models.posts.Post
import me.nathanfallet.extopy.models.posts.PostPayload
import me.nathanfallet.extopy.models.users.UserContext
import me.nathanfallet.extopy.repositories.posts.IPostsRepository
import me.nathanfallet.usecases.context.IContext
import org.jetbrains.exposed.sql.*

class DatabasePostsRepository(
    private val database: Database,
) : IPostsRepository {

    override suspend fun listDefault(limit: Long, offset: Long, context: UserContext): List<Post> {
        return database.dbQuery {
            customJoinColumnSet(context.userId)
                .join(
                    FollowersInUsers,
                    JoinType.LEFT,
                    Posts.userId,
                    FollowersInUsers.targetId
                )
                .customPostsSlice()
                .select {
                    Posts.userId eq context.userId or
                            (FollowersInUsers.userId eq context.userId and
                                    (FollowersInUsers.accepted eq true))
                }
                .groupBy(Posts.id)
                .orderBy(Posts.published to SortOrder.DESC)
                .limit(limit.toInt(), offset)
                .map { Posts.toPost(it, Users.toUser(it)) }
        }
    }

    override suspend fun listTrends(limit: Long, offset: Long, context: UserContext): List<Post> {
        return database.dbQuery {
            customJoin(context.userId)
                .selectAll()
                .groupBy(Posts.id)
                .orderBy(Posts.trendsCount to SortOrder.DESC)
                .limit(limit.toInt(), offset)
                .map { Posts.toPost(it, Users.toUser(it)) }
        }
    }

    override suspend fun listUserPosts(userId: String, limit: Long, offset: Long, context: UserContext): List<Post> {
        return database.dbQuery {
            customJoin(context.userId)
                .select { Posts.userId eq userId }
                .groupBy(Posts.id)
                .orderBy(Posts.published to SortOrder.DESC)
                .limit(limit.toInt(), offset)
                .map { Posts.toPost(it, Users.toUser(it)) }
        }
    }

    override suspend fun listReplies(postId: String, limit: Long, offset: Long, context: UserContext): List<Post> {
        return database.dbQuery {
            customJoin(context.userId)
                .select { Posts.repliedToId eq postId }
                .groupBy(Posts.id)
                .orderBy(Posts.published to SortOrder.DESC)
                .limit(limit.toInt(), offset)
                .map { Posts.toPost(it, Users.toUser(it)) }
        }
    }

    override suspend fun get(id: String, context: IContext?): Post? {
        if (context !is UserContext) return null
        return database.dbQuery {
            customJoin(context.userId)
                .select { Posts.id eq id }
                .groupBy(Posts.id)
                .map { Posts.toPost(it, Users.toUser(it)) }
                .singleOrNull()
        }
    }

    override suspend fun create(payload: PostPayload, context: IContext?): Post? {
        if (context !is UserContext) return null
        val id = database.dbQuery {
            val id = Posts.generateId()
            Posts.insert {
                it[Posts.id] = id
                it[userId] = context.userId
                it[repliedToId] = payload.repliedToId
                it[repostOfId] = payload.repostOfId
                it[body] = payload.body
                it[published] = Clock.System.now().toString()
                it[edited] = null
                it[expiration] = Clock.System.now().toString()
                it[visibility] = ""
            }
            id
        }
        return get(id, context)
    }

    override suspend fun update(id: String, payload: PostPayload, context: IContext?): Boolean {
        return database.dbQuery {
            Posts.update({ Posts.id eq id }) {
                it[body] = payload.body
                it[edited] = Clock.System.now().toString()
            }
        } == 1
    }

    override suspend fun delete(id: String, context: IContext?): Boolean {
        return database.dbQuery {
            Posts.deleteWhere {
                Op.build { Posts.id eq id }
            }
        } == 1
    }

    private fun customJoin(viewedBy: String): FieldSet {
        return customJoinColumnSet(viewedBy).customPostsSlice()
    }

    private fun customJoinColumnSet(viewedBy: String): ColumnSet {
        return Posts.join(Users, JoinType.INNER, Posts.userId, Users.id)
            .join(LikesInPosts, JoinType.LEFT, Posts.id, LikesInPosts.postId)
            .join(Posts.replies, JoinType.LEFT, Posts.id, Posts.replies[Posts.repliedToId])
            .join(Posts.reposts, JoinType.LEFT, Posts.id, Posts.reposts[Posts.repostOfId])
            .join(
                LikesInPosts.likesIn,
                JoinType.LEFT,
                Posts.id,
                LikesInPosts.likesIn[LikesInPosts.postId]
            ) { LikesInPosts.likesIn[LikesInPosts.userId] eq viewedBy }
    }

    private fun ColumnSet.customPostsSlice(additionalFields: List<Expression<*>> = listOf()): FieldSet {
        return slice(
            Posts.columns +
                    Users.id +
                    Users.displayName +
                    Users.username +
                    Users.avatar +
                    Users.verified +
                    Posts.likesCount +
                    Posts.repliesCount +
                    Posts.repostsCount +
                    Posts.likesIn +
                    additionalFields
        )
    }

}
