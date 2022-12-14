package per.zsck.music.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import per.zsck.music.entity.Music;
import per.zsck.music.service.MusicService;
import per.zsck.music.utils.FIleUtils;
import per.zsck.music.utils.MusicUtil;

import javax.annotation.Resource;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MusicServiceImpl implements MusicService {

    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private GridFsTemplate gridFsTemplate;


    @Override
    public boolean removeMusicByFileId(String fileId) {
        return mongoTemplate.remove( Query.query( Criteria.where("fileId").is( new ObjectId( fileId ) ) ), Music.class ).wasAcknowledged();
    }

    @Override
    public GridFsResource getMusicFileByFileId(String fileId) {

        GridFSFile gridFSFile = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(fileId)));
        if ( gridFSFile == null ){
            return null;
        }

        return gridFsTemplate.getResource(gridFSFile);
    }

    @Override
    public List<Music> likeMusic(String keyword) {
        return  mongoTemplate.find( Query.query(Criteria.where( "audioNameIndex" )
                .regex( Pattern.compile( ".*" + keyword + ".*" , Pattern.CASE_INSENSITIVE) )), Music.class
        );

    }

    @Override
    public ObjectId uploadMusicAndReplace(Music music, byte[] bytes) {
        String md5 = DigestUtil.md5Hex(bytes);
        GridFSFile usedFile = gridFsTemplate.findOne(Query.query(Criteria.where("metadata.MD5").is(md5)));
        if (usedFile != null){
            Objects.requireNonNull( usedFile.getMetadata() );
            log.warn( "?????? name:{}, MD5:{}, ????????????, ?????????????????????", usedFile.getFilename(), usedFile.getMetadata().get("MD5") );
            gridFsTemplate.delete( Query.query(Criteria.where("metadata.MD5").is(md5)) );

            removeMusicByFileId( usedFile.getObjectId().toHexString() );
        }

        music.setFileId(
                gridFsTemplate.store(IoUtil.toStream( bytes ), music.getFileName(), new Document("MD5", md5))
        );

        return music.getFileId();
    }

    /**
     * Music.fileId ???null??????????????????
     */
    @Override
    public Music analysisAndUpload(String fileName, byte[] bytes) {

        Music music = analysisOfMusic(fileName , bytes);

        if (uploadMusicAndReplace( music, bytes ) != null) {
            music.setUrl(MusicUtil.URL_PREFIX + music.getFileId());

            mongoTemplate.save( music);
        }


        return music;
    }


    /**
     * ????????????(?????????????????????????????????????????????),?????????????????????music???audioName(??????audioNameIndex), artist, title??????
     * ????????? bytes ??????md5??????
     */
    private Music analysisOfMusic(String fileNameWithSuffix, byte[] bytes){

        String fileNameWithNoSuffix = FIleUtils.getFileNamePrefix( fileNameWithSuffix );
        String[] fileInfo = fileNameWithNoSuffix.split(" - ");//"?????? - ??????
        Music music = new Music();

        if ( fileInfo.length == 2 ){
            music.setArtist( fileInfo[0].trim() );
            music.setTitle( fileInfo[1].trim() );
        }else if (fileInfo.length >= 2){
            music.setTitle( fileInfo[ fileInfo.length - 1 ].trim() );
            music.setArtist(
                    fileNameWithNoSuffix.substring(
                            0, fileNameWithNoSuffix.indexOf( fileInfo[ fileInfo.length - 1 ] )
                    ).trim()
            );

        }

        File tempFile = FIleUtils.createTempFileAndLog("." + FIleUtils.getFileNameSuffix(fileNameWithSuffix));

        try {

            AudioFile audioFile = AudioFileIO.read(FileUtil.writeBytes(bytes, tempFile));
            log.info("?????????????????????:{} ?????????:{} ???????????????", tempFile.getName(), bytes.length);


            MusicUtil.analysisOfAudioFile(audioFile, music);
            log.info("??????:{}, ?????????????????????:{}",  fileNameWithSuffix, tempFile.getName());


        } catch (Exception e) {
            log.warn("??????: {} - {}  ????????????????????????????????????????????????????????????????????????????????????????????????", music.getArtist(), music.getTitle());

        }

        music.setAudioName(music.getArtist() + " - " + music.getTitle());
        music.setFileName(music.getAudioName() + "." + FIleUtils.getFileNameSuffix(fileNameWithSuffix));
        music.setMd5( DigestUtil.md5Hex( bytes ) );

        FIleUtils.deleteAndLog( tempFile );

        return music;
    }

}
