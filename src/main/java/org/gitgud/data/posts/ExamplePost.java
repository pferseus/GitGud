package org.gitgud.data.posts;

import org.gitgud.core.model.StaticPost;

@SuppressWarnings("unused")
public class ExamplePost extends StaticPost {
    public ExamplePost() {
        super("""
                This is an example post,
                using block quotes, from
                a class extending StaticPost.
                """, true);
    }
}
