package com.chentsov.common.messages.requests;

        import com.chentsov.common.messages.AbstractMessage;

        import java.util.List;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents a cloud file request
 */
public class FileRequest extends AbstractMessage {

    private String destinationPath;
    private List<String> stringPaths;

    public List<String> getStringPaths() {
        return stringPaths;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public FileRequest(List<String> stringPaths, String destinationPath) {
        this.stringPaths = stringPaths;
        this.destinationPath = destinationPath;
    }

}
