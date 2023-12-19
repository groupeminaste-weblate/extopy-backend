package me.nathanfallet.extopy.usecases.users

import me.nathanfallet.extopy.models.posts.Post
import me.nathanfallet.extopy.models.users.UserContext
import me.nathanfallet.extopy.repositories.posts.IPostsRepository

class GetUserPostsUseCase(
    private val postsRepository: IPostsRepository,
) : IGetUserPostsUseCase {

    override suspend fun invoke(input1: String, input2: Long, input3: Long, input4: UserContext): List<Post> {
        return postsRepository.listUserPosts(input1, input2, input3, input4)
    }

}
