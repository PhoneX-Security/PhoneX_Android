-- phpMyAdmin SQL Dump
-- version 2.11.11
-- http://www.phpmyadmin.net
--
-- Host: 89.29.122.86
-- Generation Time: May 18, 2015 at 01:51 PM
-- Server version: 5.0.95
-- PHP Version: 5.3.6

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";

--
-- Database: `phonex_translate`
--

-- --------------------------------------------------------

--
-- Table structure for table `sourceFile`
--

CREATE TABLE IF NOT EXISTS `sourceFile` (
  `id` int(11) NOT NULL auto_increment,
  `projectId` int(11) NOT NULL,
  `filename` varchar(255) collate utf8_bin NOT NULL,
  `revision` int(11) NOT NULL,
  `filetype` enum('android_xml','ios_property','csv') collate utf8_bin NOT NULL,
  `srcLang` varchar(16) collate utf8_bin NOT NULL default 'en',
  `filedata` text collate utf8_bin NOT NULL,
  PRIMARY KEY  (`id`),
  UNIQUE KEY `projectId` (`projectId`,`filename`,`revision`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 COLLATE=utf8_bin AUTO_INCREMENT=1 ;

-- --------------------------------------------------------

--
-- Table structure for table `sourcePhrases`
--

CREATE TABLE IF NOT EXISTS `sourcePhrases` (
  `id` int(11) NOT NULL auto_increment,
  `projectId` int(11) NOT NULL,
  `revision` int(11) NOT NULL,
  `sourceFile` int(11) NOT NULL,
  `stringKey` varchar(255) collate utf8_bin NOT NULL,
  `origKey` varchar(255) collate utf8_bin default NULL,
  `pluralType` enum('none','zero','one','two','few','many','other') collate utf8_bin NOT NULL,
  `srcLang` varchar(6) collate utf8_bin NOT NULL default 'en',
  `translatable` tinyint(1) NOT NULL default '1',
  `content` text collate utf8_bin NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 COLLATE=utf8_bin AUTO_INCREMENT=1 ;

-- --------------------------------------------------------

--
-- Table structure for table `translation`
--

CREATE TABLE IF NOT EXISTS `translation` (
  `id` int(11) NOT NULL auto_increment,
  `projectId` int(11) NOT NULL,
  `revision` int(11) NOT NULL,
  `srcPhrase` int(11) NOT NULL,
  `stringKey` varchar(255) collate utf8_bin NOT NULL,
  `origKey` varchar(255) collate utf8_bin default NULL,
  `pluralType` enum('none','zero','one','two','few','many','other') collate utf8_bin NOT NULL default 'none',
  `dstLang` varchar(8) collate utf8_bin NOT NULL,
  `approveLvl` int(11) NOT NULL default '0' COMMENT 'level of approval',
  `translation` text collate utf8_bin NOT NULL,
  PRIMARY KEY  (`id`),
  UNIQUE KEY `projectId` (`projectId`,`revision`,`stringKey`,`dstLang`),
  FULLTEXT KEY `translation` (`translation`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 COLLATE=utf8_bin AUTO_INCREMENT=1 ;
