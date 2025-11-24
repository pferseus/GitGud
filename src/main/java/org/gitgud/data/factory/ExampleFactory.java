package org.gitgud.data.factory;

import org.gitgud.core.model.Post;
import org.gitgud.core.model.PostFactory;
import org.gitgud.core.model.StaticPost;

import java.util.List;

@SuppressWarnings("unused")
public class ExampleFactory implements PostFactory {
    @Override
    public List<Post> generatePosts() {
        return List.of(
                new StaticPost("Hello World", true),
                new StaticPost("This is a short messaged from a PostFactory, with wrapping!", true),
                new StaticPost("This is a long message that will not be wrapped...", false)
        );
    }
}
