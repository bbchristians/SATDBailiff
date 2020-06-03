package edu.rit.se.satd.mining.commit;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
public class FileToFileMapping {

    @Getter
    private final String f1;
    @Getter
    @Setter
    private String f1Contents;

    @Getter
    private final String f2;
    @Getter
    @Setter
    private String f2Contents;

    @Override
    public String toString() {
        return String.format("%s -> %s", this.f1, this.f2);
    }
}
