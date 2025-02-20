package me.nathanfallet.extopy.database.users

import me.nathanfallet.extopy.database.Database
import me.nathanfallet.extopy.models.users.FollowerInUser
import me.nathanfallet.extopy.models.users.FollowerInUserContext
import me.nathanfallet.extopy.repositories.users.IFollowersInUsersRepository
import me.nathanfallet.usecases.context.IContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class DatabaseFollowersInUsersRepository(
    private val database: Database,
) : IFollowersInUsersRepository {

    override suspend fun list(limit: Long, offset: Long, parentId: String, context: IContext?): List<FollowerInUser> {
        return database.dbQuery {
            customFollowersJoin()
                .select { FollowersInUsers.targetId eq parentId and (FollowersInUsers.accepted eq true) }
                .limit(limit.toInt(), offset)
                .map { FollowersInUsers.toFollowerInUser(it, Users.toUser(it), null) }
        }
    }

    override suspend fun listFollowing(
        limit: Long,
        offset: Long,
        parentId: String,
        context: IContext?,
    ): List<FollowerInUser> {
        return database.dbQuery {
            customFollowingJoin()
                .select { FollowersInUsers.userId eq parentId and (FollowersInUsers.accepted eq true) }
                .limit(limit.toInt(), offset)
                .map { FollowersInUsers.toFollowerInUser(it, null, Users.toUser(it)) }
        }
    }

    override suspend fun create(payload: Unit, parentId: String, context: IContext?): FollowerInUser? {
        if (context !is FollowerInUserContext) return null
        return database.dbQuery {
            FollowersInUsers.insert {
                it[userId] = context.userId
                it[targetId] = parentId
                it[accepted] = context.isTargetPublic
            }.resultedValues?.map { FollowersInUsers.toFollowerInUser(it, null, null) }?.singleOrNull()
        }
    }

    override suspend fun delete(id: String, parentId: String, context: IContext?): Boolean {
        return database.dbQuery {
            FollowersInUsers.deleteWhere {
                userId eq id and (targetId eq parentId)
            }
        } == 1
    }

    private fun customFollowersJoin(): FieldSet {
        return FollowersInUsers
            .join(Users, JoinType.LEFT, FollowersInUsers.userId, Users.id)
            .customUsersFollowersSlice()
    }

    private fun customFollowingJoin(): FieldSet {
        return FollowersInUsers
            .join(Users, JoinType.LEFT, FollowersInUsers.targetId, Users.id)
            .customUsersFollowersSlice()
    }

    private fun ColumnSet.customUsersFollowersSlice(additionalFields: List<Expression<*>> = listOf()): FieldSet {
        return slice(
            additionalFields +
                    FollowersInUsers.userId +
                    FollowersInUsers.targetId +
                    Users.id +
                    Users.displayName +
                    Users.username +
                    Users.avatar +
                    Users.verified
        )
    }

}
